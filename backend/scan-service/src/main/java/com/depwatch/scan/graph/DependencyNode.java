package com.depwatch.scan.graph;

/**
  * A dependency graph node. Different versions of the same package are different nodes (common in npm).
 *
  * @param ecosystem OSV ecosystem name: npm / Maven / PyPI ...
  * @param name      npm package name, or groupId:artifactId for Maven
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
