package com.depwatch.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * manifest 文件存储抽象（Claim Check 模式）。
 * 本地开发用共享 Docker 卷，生产用 S3。project-service 写，scan-service 读。
 */
public interface ManifestStorage {

    /**
     * @return storage key，写入事件供 scan-service 读取
     */
    String store(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

    InputStream load(String key) throws IOException;

    void delete(String key) throws IOException;
}
