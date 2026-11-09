package com.depwatch.common.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * S3 实现。凭证走默认链（ECS Task Role / EC2 Instance Profile / 环境变量），代码里不出现 AK/SK。
 */
public class S3ManifestStorage implements ManifestStorage {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3ManifestStorage(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
    }

    @Override
    public String store(String key, InputStream content, long sizeBytes, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(fullKey(key))
                        .contentType(contentType)
                        .contentLength(sizeBytes)
                        .build(),
                RequestBody.fromInputStream(content, sizeBytes));
        return key;
    }

    @Override
    public InputStream load(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(fullKey(key)).build());
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fullKey(key)).build());
    }

    private String fullKey(String key) {
        return prefix.isEmpty() ? key : prefix + "/" + key;
    }
}
