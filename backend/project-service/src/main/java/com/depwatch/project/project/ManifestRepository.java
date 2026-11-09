package com.depwatch.project.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ManifestRepository extends JpaRepository<Manifest, UUID> {
    List<Manifest> findByProjectIdOrderByUploadedAtDesc(UUID projectId);
}
