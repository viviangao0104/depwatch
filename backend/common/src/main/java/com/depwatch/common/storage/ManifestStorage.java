package com.depwatch.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
  * Manifest file storage abstraction (Claim Check pattern).
  * A shared Docker volume locally, S3 in production. project-service writes, scan-service reads.
 */
public interface ManifestStorage {

    /**
      * @return the storage key, carried in the event for scan-service to read
     */
    String store(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

    InputStream load(String key) throws IOException;

    void delete(String key) throws IOException;
}
