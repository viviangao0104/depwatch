package com.depwatch.project.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description
    ) {
    }

    public record ProjectResponse(UUID id, String name, String description, Instant createdAt, Instant updatedAt) {
        static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record ManifestResponse(UUID id, String fileName, String manifestType, long sizeBytes,
                                   UUID scanId, Instant uploadedAt) {
        static ManifestResponse from(Manifest m) {
            return new ManifestResponse(m.getId(), m.getFileName(), m.getManifestType(), m.getSizeBytes(),
                    m.getScanId(), m.getUploadedAt());
        }
    }

    /** 上传后立即返回 scanId，前端据此订阅 WebSocket 进度。 */
    public record UploadResponse(UUID manifestId, UUID scanId, String manifestType) {
    }
}
