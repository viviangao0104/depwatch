package com.depwatch.scan.scan;

import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
  * The dependency graph is persisted with JdbcTemplate batch writes rather than JPA.
  * A frontend project has thousands of nodes and edges; persisting them one by one through JPA is tens of times slower (first-level cache bloat, flush overhead).
  * A textbook case of "use the ORM where it fits and SQL where it fits".
 */
@Repository
public class ScanGraphStore {

    private final JdbcTemplate jdbc;

    public ScanGraphStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveGraph(UUID scanId, DependencyGraph graph) {
        Map<String, Integer> depths = graph.depths();
        List<DependencyNode> nodes = new ArrayList<>(graph.nodes());

        jdbc.batchUpdate("""
                        INSERT INTO scan_dependencies (scan_id, node_id, ecosystem, name, version, direct, depth)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                nodes, 500, (PreparedStatement ps, DependencyNode n) -> {
                    ps.setObject(1, scanId);
                    ps.setString(2, n.id());
                    ps.setString(3, n.ecosystem());
                    ps.setString(4, n.name());
                    ps.setString(5, n.version());
                    ps.setBoolean(6, graph.isDirect(n.id()));
                    ps.setInt(7, depths.getOrDefault(n.id(), -1));
                });

        List<String[]> edges = graph.edges();
        jdbc.batchUpdate("""
                        INSERT INTO scan_edges (scan_id, parent_node_id, child_node_id) VALUES (?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                edges, 500, (PreparedStatement ps, String[] e) -> {
                    ps.setObject(1, scanId);
                    ps.setString(2, e[0]);
                    ps.setString(3, e[1]);
                });
    }

    /** For scheduled rescans: copy the previous graph verbatim into the new scan (no manifest re-parse). */
    public void copyGraph(UUID fromScanId, UUID toScanId) {
        jdbc.update("""
                INSERT INTO scan_dependencies (scan_id, node_id, ecosystem, name, version, direct, depth)
                SELECT ?, node_id, ecosystem, name, version, direct, depth FROM scan_dependencies WHERE scan_id = ?
                """, toScanId, fromScanId);
        jdbc.update("""
                INSERT INTO scan_edges (scan_id, parent_node_id, child_node_id)
                SELECT ?, parent_node_id, child_node_id FROM scan_edges WHERE scan_id = ?
                """, toScanId, fromScanId);
    }

    public List<DependencyNode> loadNodes(UUID scanId) {
        return jdbc.query("SELECT ecosystem, name, version FROM scan_dependencies WHERE scan_id = ? ORDER BY id",
                (rs, i) -> new DependencyNode(rs.getString(1), rs.getString(2), rs.getString(3)), scanId);
    }

    public Set<String> loadDirectNodeIds(UUID scanId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT node_id FROM scan_dependencies WHERE scan_id = ? AND direct", String.class, scanId));
    }

    public Map<String, Set<String>> loadEdges(UUID scanId) {
        Map<String, Set<String>> out = new HashMap<>();
        jdbc.query("SELECT parent_node_id, child_node_id FROM scan_edges WHERE scan_id = ?", rs -> {
            out.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
        }, scanId);
        return out;
    }

    public List<Map<String, Object>> listDependencies(UUID scanId) {
        return jdbc.query("""
                SELECT d.node_id, d.ecosystem, d.name, d.version, d.direct, d.depth,
                       COUNT(f.id) AS finding_count
                FROM scan_dependencies d
                LEFT JOIN scan_findings f ON f.scan_id = d.scan_id AND f.node_id = d.node_id
                WHERE d.scan_id = ?
                GROUP BY d.id, d.node_id, d.ecosystem, d.name, d.version, d.direct, d.depth
                ORDER BY finding_count DESC, d.direct DESC, d.name
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", rs.getString("node_id"));
            m.put("ecosystem", rs.getString("ecosystem"));
            m.put("name", rs.getString("name"));
            m.put("version", rs.getString("version"));
            m.put("direct", rs.getBoolean("direct"));
            m.put("depth", rs.getInt("depth"));
            m.put("findingCount", rs.getInt("finding_count"));
            return m;
        }, scanId);
    }
}
