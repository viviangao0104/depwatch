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
  * Business-facing OSV client: batching and caching. HTTP details and resilience live in OsvHttpClient.
 *
  * The cache sits here rather than in OsvHttpClient so a cache hit never reaches the RateLimiter (no permit consumed).
  * Spring's @Cacheable is proxy-based, so the call must cross a bean boundary to take effect.
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
      * @param onBatchDone called after each batch with the number of nodes processed so far, used for progress events
      * @return nodeId → list of matching vulnerability IDs (nodes without vulnerabilities are absent from the map)
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

            // OSV guarantees results are in the same order as queries
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

    /** Raw vulnerability JSON, cached in Redis for 24h (TTL: spring.cache.redis.time-to-live). */
    @Cacheable(cacheNames = "osv-vuln", key = "#vulnId", unless = "#result == null")
    public String fetchVulnerabilityRaw(String vulnId) {
        return http.fetchVulnerabilityRaw(vulnId);
    }
}
