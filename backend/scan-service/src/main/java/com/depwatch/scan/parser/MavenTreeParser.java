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
 * 解析 `mvn dependency:tree` 的文本输出（含完整传递依赖）。
 *
 *   com.example:demo:jar:0.0.1-SNAPSHOT
 *   +- org.springframework.boot:spring-boot-starter-web:jar:3.3.5:compile
 *   |  +- org.springframework.boot:spring-boot-starter:jar:3.3.5:compile
 *   |  \- org.yaml:snakeyaml:jar:2.2:compile
 *   \- org.postgresql:postgresql:jar:42.7.4:runtime
 *
 * 每一层缩进固定 3 个字符（"+- " / "\- " / "|  " / "   "），深度 = 前缀长度 / 3。
 * 用一个按深度索引的栈记录"当前路径"，就能把缩进文本还原成树。
 * 兼容控制台输出里的 "[INFO] " 前缀，以及多模块项目里的多棵树。
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
        List<String> stack = new ArrayList<>();      // stack.get(d) = 深度 d 的当前节点 id
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

                if (coords.contains("omitted for")) {   // -Dverbose 模式下被仲裁掉的版本，并未真正引入
                    sawOmitted = true;
                    continue;
                }

                Coordinate c = Coordinate.parse(coords);
                if (c == null) {
                    graph.addWarning("Unrecognized line: " + coords);
                    continue;
                }
                parsedLines++;

                if (depth == 0) {                      // 模块根：不是依赖，挂到虚拟 ROOT 下
                    stack.clear();
                    stack.add(DependencyGraph.ROOT_ID);
                    continue;
                }

                DependencyNode node = DependencyNode.maven(c.groupId, c.artifactId, c.version);
                graph.addNode(node);

                // 父节点 = 上一层的当前节点；没有根行（用户只贴了子树）时退化为 ROOT
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
     * group:artifact:type[:classifier]:version[:scope] 可能带 " (optional)" 后缀。
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
