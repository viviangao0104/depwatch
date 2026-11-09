package com.depwatch.scan.osv;

import com.depwatch.scan.graph.DependencyNode;
import com.depwatch.scan.osv.OsvDtos.PackageRef;
import com.depwatch.scan.osv.OsvDtos.Query;
import com.depwatch.scan.osv.OsvDtos.QueryBatchRequest;
import com.depwatch.scan.osv.OsvDtos.QueryBatchResponse;
import com.depwatch.scan.osv.OsvDtos.QueryResult;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 面向业务的 OSV 客户端：分批、缓存。HTTP 细节和容错在 OsvHttpClient。
 *
 * 缓存放在这一层而不是 OsvHttpClient，是为了让缓存命中时根本不经过 RateLimiter（不消耗令牌）。
 * Spring 的 @Cacheable 是基于代理的，所以必须跨 bean 调用才生效。
 */
@Service
public class OsvClient {

    private final OsvHttpClient http;
    private final int batchSize;

    public OsvClient(OsvHttpClient http, OsvProperties props) {
        this.http = http;
        this.batchSize = Math.max(1, Math.min(props.batchSize(), 1000));
    }

    /**
     * @param onBatchDone 每批完成后回调已处理的节点数，用于发进度事件
     * @return nodeId → 命中的漏洞 ID 列表（无漏洞的节点不出现在 map 里）
     */
    public Map<String, List<String>> findVulnerabilities(List<DependencyNode> nodes, IntConsumer onBatchDone) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int start = 0; start < nodes.size(); start += batchSize) {
            List<DependencyNode> slice = nodes.subList(start, Math.min(start + batchSize, nodes.size()));
            List<Query> queries = slice.stream()
                    .map(n -> new Query(new PackageRef(n.name(), n.ecosystem()), n.version()))
                    .toList();

            QueryBatchResponse resp = http.queryBatch(new QueryBatchRequest(queries));
            List<QueryResult> results = resp == null || resp.results() == null ? List.of() : resp.results();

            // OSV 保证 results 与 queries 顺序一一对应
            for (int i = 0; i < slice.size() && i < results.size(); i++) {
                QueryResult r = results.get(i);
                if (r != null && r.vulns() != null && !r.vulns().isEmpty()) {
                    List<String> ids = new ArrayList<>(r.vulns().size());
                    r.vulns().forEach(v -> ids.add(v.id()));
                    result.put(slice.get(i).id(), ids);
                }
            }
            onBatchDone.accept(Math.min(start + batchSize, nodes.size()));
        }
        return result;
    }

    /** 漏洞详情原始 JSON，Redis 缓存 24h（TTL 见 spring.cache.redis.time-to-live）。 */
    @Cacheable(cacheNames = "osv-vuln", key = "#vulnId", unless = "#result == null")
    public String fetchVulnerabilityRaw(String vulnId) {
        return http.fetchVulnerabilityRaw(vulnId);
    }
}
