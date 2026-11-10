package com.depwatch.scan.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
  * Directed dependency graph (adjacency list). root is the scanned project itself and is never queried for vulnerabilities.
 *
  * Why a graph and not a tree: after npm dedup one package can have several parents; Maven's dependency:tree is a tree,
  * but treating both as graphs lets one algorithm compute "shortest introduction path" and "depth" for both.
 */
public class DependencyGraph {

    public static final String ROOT_ID = "__root__";

    private final Map<String, DependencyNode> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> children = new HashMap<>();
    private final Map<String, Set<String>> parents = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public void addNode(DependencyNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    public void addEdge(String parentId, String childId) {
        if (parentId.equals(childId)) return;
        children.computeIfAbsent(parentId, k -> new LinkedHashSet<>()).add(childId);
        parents.computeIfAbsent(childId, k -> new LinkedHashSet<>()).add(parentId);
    }

    public void addWarning(String w) {
        warnings.add(w);
    }

    public Collection<DependencyNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public DependencyNode node(String id) {
        return nodes.get(id);
    }

    public Set<String> childrenOf(String id) {
        return children.getOrDefault(id, Set.of());
    }

    public Set<String> parentsOf(String id) {
        return parents.getOrDefault(id, Set.of());
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public boolean isDirect(String id) {
        return childrenOf(ROOT_ID).contains(id);
    }

    /** All edges, for batch persistence. */
    public List<String[]> edges() {
        List<String[]> out = new ArrayList<>();
        children.forEach((p, cs) -> cs.forEach(c -> out.add(new String[]{p, c})));
        return out;
    }

    /**
      * BFS depth of every node from root (direct dependency = 1). Unreachable nodes get -1.
     */
    public Map<String, Integer> depths() {
        Map<String, Integer> depth = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        depth.put(ROOT_ID, 0);
        queue.add(ROOT_ID);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = depth.get(cur);
            for (String child : childrenOf(cur)) {
                if (!depth.containsKey(child)) {
                    depth.put(child, d + 1);
                    queue.add(child);
                }
            }
        }
        Map<String, Integer> result = new HashMap<>();
        for (String id : nodes.keySet()) {
            result.put(id, depth.getOrDefault(id, -1));
        }
        return result;
    }

    /**
      * Shortest path from root to the target (root excluded). Answers "which package pulled in this vulnerable one?".
      * Static so scan-service can reuse it on edges loaded from the DB.
     */
    public static List<String> shortestPath(Map<String, Set<String>> childrenByParent, String target) {
        if (ROOT_ID.equals(target)) return List.of();
        Map<String, String> prev = new HashMap<>();
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(ROOT_ID);
        seen.add(ROOT_ID);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String child : childrenByParent.getOrDefault(cur, Set.of())) {
                if (seen.add(child)) {
                    prev.put(child, cur);
                    if (child.equals(target)) {
                        List<String> path = new ArrayList<>();
                        for (String n = target; n != null && !ROOT_ID.equals(n); n = prev.get(n)) {
                            path.add(0, n);
                        }
                        return path;
                    }
                    queue.add(child);
                }
            }
        }
        return List.of();
    }
}
