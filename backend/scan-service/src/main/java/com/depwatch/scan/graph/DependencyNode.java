package com.depwatch.scan.graph;

/**
 * 依赖图节点。同一个包的不同版本是不同节点（npm 里很常见）。
 *
 * @param ecosystem OSV 生态名：npm / Maven / PyPI ...
 * @param name      npm 包名 或 Maven 的 groupId:artifactId
 */
public record DependencyNode(String ecosystem, String name, String version) {

    public String id() {
        return ecosystem + ":" + name + "@" + version;
    }

    public static DependencyNode npm(String name, String version) {
        return new DependencyNode(Ecosystems.NPM, name, version);
    }

    public static DependencyNode maven(String groupId, String artifactId, String version) {
        return new DependencyNode(Ecosystems.MAVEN, groupId + ":" + artifactId, version);
    }
}
