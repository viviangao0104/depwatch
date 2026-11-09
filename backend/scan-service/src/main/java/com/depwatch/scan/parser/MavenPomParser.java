package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 pom.xml —— 只能拿到直接依赖。
 *
 * 真正的传递依赖需要下载所有父 POM / BOM 并执行 Maven 的版本仲裁，等于重写半个 Maven，
 * 所以对 pom.xml 我们只做直接依赖，并提示用户上传 `mvn dependency:tree` 输出。
 *
 * 要处理的细节：
 *  - ${property} 占位符（<properties> 里定义的、${project.version} 等内置的）
 *  - <dependencyManagement> 里统一声明的版本
 *  - XXE 防护：用户上传的 XML 一律禁用 DTD 和外部实体
 */
@Component
public class MavenPomParser implements ManifestParser {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String type() {
        return "MAVEN_POM";
    }

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith("pom.xml");
    }

    @Override
    public DependencyGraph parse(InputStream in) throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(false);
            doc = f.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            throw new ManifestParseException("Invalid pom.xml: " + e.getMessage(), e);
        }

        Element project = doc.getDocumentElement();
        Map<String, String> props = collectProperties(project);

        DependencyGraph graph = new DependencyGraph();
        graph.addWarning("pom.xml only yields direct dependencies. Upload `mvn dependency:tree -DoutputFile=dependency-tree.txt` for the full graph.");

        Map<String, String> managed = new HashMap<>();
        Element depMgmt = firstChild(project, "dependencyManagement");
        if (depMgmt != null) {
            Element deps = firstChild(depMgmt, "dependencies");
            if (deps != null) {
                for (Element d : children(deps, "dependency")) {
                    String g = resolve(text(d, "groupId"), props);
                    String a = resolve(text(d, "artifactId"), props);
                    String v = resolve(text(d, "version"), props);
                    if (g != null && a != null && v != null) managed.put(g + ":" + a, v);
                }
            }
        }

        Element deps = firstChild(project, "dependencies");
        if (deps == null) {
            graph.addWarning("No <dependencies> section found");
            return graph;
        }
        int unresolved = 0;
        for (Element d : children(deps, "dependency")) {
            String g = resolve(text(d, "groupId"), props);
            String a = resolve(text(d, "artifactId"), props);
            String v = resolve(text(d, "version"), props);
            if (g == null || a == null) continue;
            if (v == null) v = managed.get(g + ":" + a);
            if (v == null || v.contains("${")) {
                unresolved++;
                continue;   // 版本由父 POM / BOM 管理，无法离线解析
            }
            DependencyNode node = DependencyNode.maven(g, a, v);
            graph.addNode(node);
            graph.addEdge(DependencyGraph.ROOT_ID, node.id());
        }
        if (unresolved > 0) {
            graph.addWarning(unresolved + " dependencies have versions managed by a parent POM/BOM and were skipped");
        }
        return graph;
    }

    private Map<String, String> collectProperties(Element project) {
        Map<String, String> props = new HashMap<>();
        Element parent = firstChild(project, "parent");
        String version = text(project, "version");
        if (version == null && parent != null) version = text(parent, "version");
        String groupId = text(project, "groupId");
        if (groupId == null && parent != null) groupId = text(parent, "groupId");
        if (version != null) {
            props.put("project.version", version);
            props.put("version", version);
            props.put("pom.version", version);
        }
        if (groupId != null) {
            props.put("project.groupId", groupId);
            props.put("pom.groupId", groupId);
        }
        Element properties = firstChild(project, "properties");
        if (properties != null) {
            NodeList list = properties.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node n = list.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    props.put(n.getNodeName(), n.getTextContent().trim());
                }
            }
        }
        return props;
    }

    /** 递归展开 ${...}，最多 5 层防止循环引用。 */
    static String resolve(String value, Map<String, String> props) {
        if (value == null) return null;
        String cur = value;
        for (int i = 0; i < 5 && cur.contains("${"); i++) {
            Matcher m = PLACEHOLDER.matcher(cur);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String rep = props.get(m.group(1));
                m.appendReplacement(sb, Matcher.quoteReplacement(rep != null ? rep : m.group(0)));
            }
            m.appendTail(sb);
            if (sb.toString().equals(cur)) break;
            cur = sb.toString();
        }
        return cur.trim();
    }

    private static Element firstChild(Element parent, String name) {
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) return (Element) n;
        }
        return null;
    }

    private static java.util.List<Element> children(Element parent, String name) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) out.add((Element) n);
        }
        return out;
    }

    private static String text(Element parent, String name) {
        Element e = firstChild(parent, name);
        if (e == null) return null;
        String t = e.getTextContent();
        return t == null || t.isBlank() ? null : t.trim();
    }
}
