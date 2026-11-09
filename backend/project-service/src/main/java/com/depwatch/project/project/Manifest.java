package com.depwatch.project.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manifests")
public class Manifest {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "manifest_type", nullable = false)
    private String manifestType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    protected Manifest() {
    }

    public Manifest(UUID projectId, String fileName, String storageKey, String manifestType, long sizeBytes, UUID scanId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.manifestType = manifestType;
        this.sizeBytes = sizeBytes;
        this.scanId = scanId;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public String getManifestType() { return manifestType; }
    public long getSizeBytes() { return sizeBytes; }
    public UUID getScanId() { return scanId; }
    public Instant getUploadedAt() { return uploadedAt; }
}
