package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenPomParserTest {

    private final MavenPomParser parser = new MavenPomParser();

    @Test
    void resolvesPropertiesAndDependencyManagement() throws Exception {
        String pom = """
                <project>
                  <groupId>com.example</groupId><artifactId>demo</artifactId><version>1.0.0</version>
                  <properties>
                    <jackson.version>2.17.2</jackson.version>
                    <guava.base>33.0.0</guava.base>
                    <guava.version>${guava.base}-jre</guava.version>
                  </properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId><version>3.14.0</version></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>${jackson.version}</version></dependency>
                    <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency>
                    <dependency><groupId>com.example</groupId><artifactId>sibling</artifactId><version>${project.version}</version></dependency>
                  </dependencies>
                </project>
                """;
        DependencyGraph g = parser.parse(new ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));

        assertThat(g.nodes()).extracting(DependencyNode::id).containsExactlyInAnyOrder(
                "Maven:com.fasterxml.jackson.core:jackson-databind@2.17.2",
                "Maven:com.google.guava:guava@33.0.0-jre",            // 嵌套占位符
                "Maven:org.apache.commons:commons-lang3@3.14.0",       // 来自 dependencyManagement
                "Maven:com.example:sibling@1.0.0");                    // ${project.version}
        // spring-boot-starter 没版本（由父 POM 管理）→ 跳过并告警
        assertThat(g.warnings()).anyMatch(w -> w.contains("1 dependencies have versions managed"));
        assertThat(g.childrenOf(DependencyGraph.ROOT_ID)).hasSize(4);
    }

    @Test
    void blocksExternalEntities() {
        String evil = """
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <project><dependencies><dependency><groupId>&xxe;</groupId></dependency></dependencies></project>
                """;
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(evil.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ManifestParseException.class);
    }
}
