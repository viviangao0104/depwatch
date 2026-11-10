package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
  * Parses the text output of `mvn dependency:tree` (full transitive dependencies).
 *
 *   com.example:demo:jar:0.0.1-SNAPSHOT
 *   +- org.springframework.boot:spring-boot-starter-web:jar:3.3.5:compile
 *   |  +- org.springframework.boot:spring-boot-starter:jar:3.3.5:compile
 *   |  \- org.yaml:snakeyaml:jar:2.2:compile
 *   \- org.postgresql:postgresql:jar:42.7.4:runtime
 *
  * Each level is indented by exactly 3 characters ("+- " / "\- " / "|  " / "   "); depth = prefix length / 3.
  * A stack indexed by depth tracks the "current path", which is enough to rebuild the tree from indented text.
  * Tolerates the "[INFO] " prefix of console output and the multiple trees of a multi-module build.
 */
@Component
public class MavenTreeParser implements ManifestParser {

    @Override
    public String type() {
        return "MAVEN_TREE";
    }

    @Override
    public boolean supports(String fileName) {
        String f = fileName.toLowerCase(Locale.ROOT);
        return f.contains("dependency-tree") || f.contains("deptree") || f.endsWith("tree.txt");
    }

    @Override
    public DependencyGraph parse(InputStream in) throws IOException {
        DependencyGraph graph = new DependencyGraph();
        List<String> stack = new ArrayList<>();      // stack.get(d) = current node id at depth d
        boolean sawOmitted = false;
        int parsedLines = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = stripLogPrefix(raw);
                if (line.isBlank() || line.startsWith("---") || line.startsWith("BUILD") || line.startsWith("Total time")) {
                    continue;
                }

                int i = 0;
                while (i < line.length() && isTreeChar(line.charAt(i))) i++;
                int depth = i / 3;
                String coords = line.substring(i).trim();
                if (coords.isEmpty() || !coords.contains(":")) continue;

                if (coords.contains("omitted for")) {   // -Dverbose shows versions lost in mediation; they were never actually pulled in
                    sawOmitted = true;
                    continue;
                }

                Coordinate c = Coordinate.parse(coords);
                if (c == null) {
                    graph.addWarning("Unrecognized line: " + coords);
                    continue;
                }
                parsedLines++;

                if (depth == 0) {                      // module root: not a dependency, hangs under the virtual ROOT
                    stack.clear();
                    stack.add(DependencyGraph.ROOT_ID);
                    continue;
                }

                DependencyNode node = DependencyNode.maven(c.groupId, c.artifactId, c.version);
                graph.addNode(node);

                // parent = current node one level up; falls back to ROOT when there is no root line (user pasted a subtree)
                String parent = depth - 1 < stack.size() ? stack.get(depth - 1) : DependencyGraph.ROOT_ID;
                graph.addEdge(parent, node.id());

                while (stack.size() > depth) stack.remove(stack.size() - 1);
                while (stack.size() < depth) stack.add(DependencyGraph.ROOT_ID);
                stack.add(node.id());
            }
        }

        if (parsedLines == 0) {
            throw new ManifestParseException("No Maven coordinates found. Expected output of `mvn dependency:tree`");
        }
        if (sawOmitted) {
            graph.addWarning("Verbose tree detected; omitted (conflict/duplicate) versions were ignored");
        }
        return graph;
    }

    private static boolean isTreeChar(char ch) {
        return ch == '+' || ch == '-' || ch == '\\' || ch == '|' || ch == ' ';
    }

    private static String stripLogPrefix(String line) {
        String t = line;
        if (t.startsWith("[INFO]")) t = t.substring(6);
        else if (t.startsWith("[WARNING]")) return "";
        else if (t.startsWith("[ERROR]")) return "";
        return t.startsWith(" ") ? t.substring(1) : t;
    }

    /**
      * group:artifact:type[:classifier]:version[:scope], possibly followed by " (optional)".
     */
    record Coordinate(String groupId, String artifactId, String version) {
        static Coordinate parse(String s) {
            String clean = s;
            int paren = clean.indexOf(" (");
            if (paren > 0) clean = clean.substring(0, paren);
            int dash = clean.indexOf(" - ");
            if (dash > 0) clean = clean.substring(0, dash);
            String[] p = clean.trim().split(":");
            if (p.length < 4) return null;
            // 4: g:a:type:version      5: g:a:type:version:scope      6: g:a:type:classifier:version:scope
            String version = p.length >= 6 ? p[4] : p[3];
            if (version.isBlank()) return null;
            return new Coordinate(p[0], p[1], version);
        }
    }
}
