package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
  * Parses package-lock.json (lockfileVersion 1 / 2 / 3).
 *
  * In v2/v3, "packages" is a flat map keyed by install path:
  *   ""                                    → the project itself (root)
  *   "node_modules/a"                      → a, hoisted to the top level
  *   "node_modules/a/node_modules/b"       → b, nested under a because of a version conflict
 *
  * Rebuilding edges means simulating Node's module resolution: when package P depends on N, look for P/node_modules/N first,
  * then walk up to the parent node_modules, all the way to the project root. This is exactly npm's hoisting rule.
 *
  * v1 has no "packages", only a nested "dependencies" tree; it is flattened into the same path map and then handled identically.
 */
@Component
public class NpmLockfileParser implements ManifestParser {

    private static final String NODE_MODULES = "node_modules/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() {
        return "NPM_LOCKFILE";
    }

    @Override
    public boolean supports(String fileName) {
        String f = fileName.toLowerCase(Locale.ROOT);
        return f.endsWith("package-lock.json") || f.endsWith("npm-shrinkwrap.json");
    }

    @Override
    public DependencyGraph parse(InputStream in) throws IOException {
        JsonNode root = mapper.readTree(in);
        if (root == null || !root.isObject()) {
            throw new ManifestParseException("package-lock.json is not a JSON object");
        }
        int version = root.path("lockfileVersion").asInt(1);

        Map<String, JsonNode> packages = new LinkedHashMap<>();
        if (version >= 2 && root.has("packages")) {
            root.get("packages").fields().forEachRemaining(e -> packages.put(e.getKey(), e.getValue()));
        } else if (root.has("dependencies")) {
            flattenV1(root.get("dependencies"), "", packages);
        } else {
            throw new ManifestParseException("package-lock.json has neither 'packages' nor 'dependencies'");
        }

        DependencyGraph graph = new DependencyGraph();
        Map<String, String> pathToNodeId = new HashMap<>();

        // 1. Nodes
        for (Map.Entry<String, JsonNode> e : packages.entrySet()) {
            String path = e.getKey();
            JsonNode pkg = e.getValue();
            if (path.isEmpty()) continue;                       // the root itself
            if (pkg.path("link").asBoolean(false)) {
                graph.addWarning("Skipped workspace link: " + path);
                continue;
            }
            if (!path.contains(NODE_MODULES)) {                 // local workspace package, not a third-party dependency
                continue;
            }
            String name = nameFromPath(path);
            String ver = pkg.path("version").asText(null);
            if (ver == null || ver.isBlank()) {
                graph.addWarning("No version for " + path + ", skipped");
                continue;
            }
            DependencyNode node = DependencyNode.npm(name, ver);
            graph.addNode(node);
            pathToNodeId.put(path, node.id());
        }

        // 2. Edges
        if (version >= 2) {
            JsonNode rootPkg = packages.getOrDefault("", mapper.createObjectNode());
            addEdges(graph, packages, pathToNodeId, "", DependencyGraph.ROOT_ID, rootPkg,
                    "dependencies", "devDependencies", "optionalDependencies", "peerDependencies");
            for (Map.Entry<String, String> e : pathToNodeId.entrySet()) {
                JsonNode pkg = packages.get(e.getKey());
                addEdges(graph, packages, pathToNodeId, e.getKey(), e.getValue(), pkg,
                        "dependencies", "optionalDependencies", "peerDependencies");
            }
        } else {
            // v1: relationships live in "requires"; direct deps are unknown, approximated as "top-level packages with no parent"
            for (Map.Entry<String, String> e : pathToNodeId.entrySet()) {
                JsonNode pkg = packages.get(e.getKey());
                addEdges(graph, packages, pathToNodeId, e.getKey(), e.getValue(), pkg, "requires");
            }
            for (Map.Entry<String, String> e : pathToNodeId.entrySet()) {
                boolean topLevel = e.getKey().indexOf(NODE_MODULES) == e.getKey().lastIndexOf(NODE_MODULES);
                if (topLevel && graph.parentsOf(e.getValue()).isEmpty()) {
                    graph.addEdge(DependencyGraph.ROOT_ID, e.getValue());
                }
            }
            graph.addWarning("lockfileVersion 1: direct dependencies are inferred; upgrade npm to get an exact graph");
        }
        return graph;
    }

    private void addEdges(DependencyGraph graph, Map<String, JsonNode> packages, Map<String, String> pathToNodeId,
                          String fromPath, String fromNodeId, JsonNode pkg, String... depFields) {
        for (String field : depFields) {
            JsonNode deps = pkg.path(field);
            if (!deps.isObject()) continue;
            Iterator<String> names = deps.fieldNames();
            while (names.hasNext()) {
                String depName = names.next();
                String resolvedPath = resolve(packages, fromPath, depName);
                if (resolvedPath == null) continue;              // optional / peer dependency not installed
                String childId = pathToNodeId.get(resolvedPath);
                if (childId != null) graph.addEdge(fromNodeId, childId);
            }
        }
    }

    /** Node module resolution: walk up from fromPath looking for node_modules/<name>. */
    static String resolve(Map<String, JsonNode> packages, String fromPath, String name) {
        String dir = fromPath;
        while (true) {
            String candidate = (dir.isEmpty() ? "" : dir + "/") + NODE_MODULES + name;
            if (packages.containsKey(candidate)) return candidate;
            if (dir.isEmpty()) return null;
            int idx = dir.lastIndexOf("/" + NODE_MODULES);
            dir = idx < 0 ? "" : dir.substring(0, idx);
        }
    }

    /** "node_modules/a/node_modules/@scope/b" → "@scope/b" */
    static String nameFromPath(String path) {
        int idx = path.lastIndexOf(NODE_MODULES);
        return idx < 0 ? path : path.substring(idx + NODE_MODULES.length());
    }

    /** Flattens the nested v1 structure into path → entry. */
    private void flattenV1(JsonNode deps, String parentPath, Map<String, JsonNode> out) {
        deps.fields().forEachRemaining(e -> {
            String path = (parentPath.isEmpty() ? "" : parentPath + "/") + NODE_MODULES + e.getKey();
            out.put(path, e.getValue());
            JsonNode nested = e.getValue().path("dependencies");
            if (nested.isObject()) flattenV1(nested, path, out);
        });
    }
}
