package com.depwatch.project.project;

import com.depwatch.common.events.ScanRequestedEvent;
import com.depwatch.common.events.Topics;
import com.depwatch.common.security.UserContext;
import com.depwatch.common.storage.ManifestStorage;
import com.depwatch.common.web.ApiException;
import com.depwatch.project.outbox.OutboxService;
import com.depwatch.project.project.ProjectDtos.CreateProjectRequest;
import com.depwatch.project.project.ProjectDtos.ManifestResponse;
import com.depwatch.project.project.ProjectDtos.ProjectResponse;
import com.depwatch.project.project.ProjectDtos.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projects;
    private final ManifestRepository manifests;
    private final ManifestStorage storage;
    private final OutboxService outbox;

    public ProjectService(ProjectRepository projects, ManifestRepository manifests,
                          ManifestStorage storage, OutboxService outbox) {
        this.projects = projects;
        this.manifests = manifests;
        this.storage = storage;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projects.findByOwnerIdOrderByUpdatedAtDesc(UserContext.require().id())
                .stream().map(ProjectResponse::from).toList();
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest req) {
        UUID owner = UserContext.require().id();
        if (projects.existsByOwnerIdAndNameIgnoreCase(owner, req.name())) {
            throw ApiException.conflict("Project name already exists");
        }
        return ProjectResponse.from(projects.save(new Project(owner, req.name().trim(), req.description())));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return ProjectResponse.from(owned(id));
    }

    @Transactional
    public ProjectResponse update(UUID id, CreateProjectRequest req) {
        Project p = owned(id);
        p.update(req.name().trim(), req.description());
        return ProjectResponse.from(p);
    }

    @Transactional
    public void delete(UUID id) {
        Project p = owned(id);
        // 先删存储文件（best effort），再删 DB（manifests 级联）
        manifests.findByProjectIdOrderByUploadedAtDesc(id).forEach(m -> {
            try {
                storage.delete(m.getStorageKey());
            } catch (IOException e) {
                log.warn("Failed to delete manifest file {}", m.getStorageKey(), e);
            }
        });
        projects.delete(p);
    }

    @Transactional(readOnly = true)
    public List<ManifestResponse> listManifests(UUID projectId) {
        owned(projectId);
        return manifests.findByProjectIdOrderByUploadedAtDesc(projectId).stream().map(ManifestResponse::from).toList();
    }

    /**
     * 上传 manifest 并请求扫描。
     * 文件写入存储 → 同一事务里写 manifests + outbox → 返回 scanId。
     * 如果事务回滚（比如 DB 挂了），存储里会留下孤儿文件，可以用定时任务清理；
     * 反过来（DB 成功、文件没写）是不可接受的，所以文件先写。
     */
    @Transactional
    public UploadResponse upload(UUID projectId, MultipartFile file) {
        Project project = owned(projectId);
        if (file.isEmpty()) throw ApiException.badRequest("Empty file");

        ManifestType type = ManifestType.detect(file.getOriginalFilename());
        UUID scanId = UUID.randomUUID();
        String key = "%s/%s/%s".formatted(project.getOwnerId(), projectId, scanId + "-" + sanitize(file.getOriginalFilename()));

        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize(), file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store manifest", e);
        }

        Manifest manifest = manifests.save(new Manifest(projectId, file.getOriginalFilename(), key,
                type.name(), file.getSize(), scanId));

        ScanRequestedEvent event = ScanRequestedEvent.of(scanId, projectId, project.getName(),
                project.getOwnerId(), key, file.getOriginalFilename());
        // key 用 projectId：同一项目的扫描落在同一分区，按序处理
        outbox.append("Project", projectId, Topics.SCAN_REQUESTED, projectId.toString(), event);

        log.info("Manifest {} uploaded for project {}, scan {} requested", manifest.getId(), projectId, scanId);
        return new UploadResponse(manifest.getId(), scanId, type.name());
    }

    private Project owned(UUID id) {
        return projects.findByIdAndOwnerId(id, UserContext.require().id())
                .orElseThrow(() -> ApiException.notFound("Project"));
    }

    private static String sanitize(String name) {
        return name == null ? "manifest" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
