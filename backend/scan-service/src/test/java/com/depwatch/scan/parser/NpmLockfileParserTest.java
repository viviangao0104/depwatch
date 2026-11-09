package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NpmLockfileParserTest {

    private final NpmLockfileParser parser = new NpmLockfileParser();

    @Test
    void parsesV3LockfileWithNestedVersionConflict() throws Exception {
        // app 依赖 a@1 和 b@2；a 依赖 b@1 → b@1 被嵌套安装在 a 下面
        String json = """
                {
                  "name": "app", "lockfileVersion": 3,
                  "packages": {
                    "": { "name": "app", "dependencies": { "a": "^1.0.0", "b": "^2.0.0" }, "devDependencies": { "jest": "^29" } },
                    "node_modules/a": { "version": "1.0.0", "dependencies": { "b": "^1.0.0" } },
                    "node_modules/a/node_modules/b": { "version": "1.5.0" },
                    "node_modules/b": { "version": "2.0.0" },
                    "node_modules/jest": { "version": "29.0.0", "dev": true }
                  }
                }
                """;
        DependencyGraph g = parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(g.nodes()).extracting(DependencyNode::id)
                .containsExactlyInAnyOrder("npm:a@1.0.0", "npm:b@1.5.0", "npm:b@2.0.0", "npm:jest@29.0.0");

        // 直接依赖
        assertThat(g.childrenOf(DependencyGraph.ROOT_ID)).containsExactlyInAnyOrder("npm:a@1.0.0", "npm:b@2.0.0", "npm:jest@29.0.0");
        // a 依赖的是嵌套的 b@1.5.0，不是顶层的 b@2.0.0 —— 这就是 Node 模块解析规则
        assertThat(g.childrenOf("npm:a@1.0.0")).containsExactly("npm:b@1.5.0");

        Map<String, Integer> depths = g.depths();
        assertThat(depths.get("npm:a@1.0.0")).isEqualTo(1);
        assertThat(depths.get("npm:b@1.5.0")).isEqualTo(2);
        assertThat(depths.get("npm:b@2.0.0")).isEqualTo(1);

        List<String> path = DependencyGraph.shortestPath(
                Map.of(DependencyGraph.ROOT_ID, g.childrenOf(DependencyGraph.ROOT_ID), "npm:a@1.0.0", g.childrenOf("npm:a@1.0.0")),
                "npm:b@1.5.0");
        assertThat(path).containsExactly("npm:a@1.0.0", "npm:b@1.5.0");
    }

    @Test
    void resolvesScopedPackagesAndSkipsWorkspaceLinks() throws Exception {
        String json = """
                {
                  "lockfileVersion": 2,
                  "packages": {
                    "": { "dependencies": { "@scope/pkg": "1.0.0", "local-lib": "*" } },
                    "node_modules/@scope/pkg": { "version": "1.0.0" },
                    "node_modules/local-lib": { "resolved": "packages/local-lib", "link": true },
                    "packages/local-lib": { "version": "0.0.1" }
                  }
                }
                """;
        DependencyGraph g = parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertThat(g.nodes()).extracting(DependencyNode::name).containsExactly("@scope/pkg");
        assertThat(g.warnings()).anyMatch(w -> w.contains("workspace link"));
    }

    @Test
    void nodeResolutionWalksUpTheTree() {
        Map<String, com.fasterxml.jackson.databind.JsonNode> pkgs = new java.util.HashMap<>();
        pkgs.put("node_modules/x", null);
        pkgs.put("node_modules/a/node_modules/x", null);
        assertThat(NpmLockfileParser.resolve(pkgs, "node_modules/a/node_modules/b", "x"))
                .isEqualTo("node_modules/a/node_modules/x");
        assertThat(NpmLockfileParser.resolve(pkgs, "node_modules/c", "x")).isEqualTo("node_modules/x");
        assertThat(NpmLockfileParser.resolve(pkgs, "node_modules/c", "missing")).isNull();
        assertThat(NpmLockfileParser.nameFromPath("node_modules/a/node_modules/@s/b")).isEqualTo("@s/b");
    }
}
