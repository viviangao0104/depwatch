package com.depwatch.scan.osv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OSV API 请求/响应模型（只映射我们用到的字段）。
 * https://google.github.io/osv.dev/post-v1-querybatch/
 */
public final class OsvDtos {

    private OsvDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PackageRef(String name, String ecosystem) {
    }

    /** OSV 的字段名是 "package"（Java 关键字），用 @JsonProperty 映射。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Query(@JsonProperty("package") PackageRef pkg, String version) {
    }

    public record QueryBatchRequest(List<Query> queries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VulnRef(String id, String modified) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueryResult(List<VulnRef> vulns, @JsonProperty("next_page_token") String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueryBatchResponse(List<QueryResult> results) {
    }
}
