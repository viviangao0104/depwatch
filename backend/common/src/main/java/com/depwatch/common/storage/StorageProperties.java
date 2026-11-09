package com.depwatch.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * depwatch.storage.type=local|s3
 * depwatch.storage.local.root=/data/manifests
 * depwatch.storage.s3.bucket=... / prefix=... / region=...
 */
@ConfigurationProperties(prefix = "depwatch.storage")
public class StorageProperties {

    private String type = "local";
    private Local local = new Local();
    private S3 s3 = new S3();

    public static class Local {
        private String root = "./data/manifests";

        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
    }

    public static class S3 {
        private String bucket;
        private String prefix = "manifests";
        private String region = "us-east-1";

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Local getLocal() { return local; }
    public void setLocal(Local local) { this.local = local; }
    public S3 getS3() { return s3; }
    public void setS3(S3 s3) { this.s3 = s3; }
}
