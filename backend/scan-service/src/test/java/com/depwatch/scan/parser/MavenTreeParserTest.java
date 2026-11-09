package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenTreeParserTest {

    private final MavenTreeParser parser = new MavenTreeParser();

    @Test
    void rebuildsTreeFromIndentation() throws Exception {
        String tree = """
                [INFO] com.example:demo:jar:0.0.1-SNAPSHOT
                [INFO] +- org.springframework.boot:spring-boot-starter-web:jar:3.3.5:compile
                [INFO] |  +- org.springframework.boot:spring-boot-starter:jar:3.3.5:compile
                [INFO] |  |  \\- org.yaml:snakeyaml:jar:2.2:compile
                [INFO] |  \\- org.springframework:spring-webmvc:jar:6.1.14:compile
                [INFO] \\- org.postgresql:postgresql:jar:42.7.4:runtime
                [INFO] ------------------------------------------------------------------------
                [INFO] BUILD SUCCESS
                """;
        DependencyGraph g = parser.parse(new ByteArrayInputStream(tree.getBytes(StandardCharsets.UTF_8)));

        assertThat(g.nodes()).hasSize(5);
        assertThat(g.childrenOf(DependencyGraph.ROOT_ID)).containsExactlyInAnyOrder(
                "Maven:org.springframework.boot:spring-boot-starter-web@3.3.5",
                "Maven:org.postgresql:postgresql@42.7.4");
        assertThat(g.childrenOf("Maven:org.springframework.boot:spring-boot-starter-web@3.3.5")).containsExactlyInAnyOrder(
                "Maven:org.springframework.boot:spring-boot-starter@3.3.5",
                "Maven:org.springframework:spring-webmvc@6.1.14");
        assertThat(g.childrenOf("Maven:org.springframework.boot:spring-boot-starter@3.3.5"))
                .containsExactly("Maven:org.yaml:snakeyaml@2.2");
        assertThat(g.depths().get("Maven:org.yaml:snakeyaml@2.2")).isEqualTo(3);
    }

    @Test
    void handlesClassifierAndOptionalSuffix() {
        var c = MavenTreeParser.Coordinate.parse("io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.1.100.Final:compile (optional)");
        assertThat(c.groupId()).isEqualTo("io.netty");
        assertThat(c.artifactId()).isEqualTo("netty-transport-native-epoll");
        assertThat(c.version()).isEqualTo("4.1.100.Final");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("hello world".getBytes())))
                .isInstanceOf(ManifestParseException.class);
    }
}
