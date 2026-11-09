package com.depwatch.project.project;

import com.depwatch.common.web.ApiException;

import java.util.Locale;

/**
 * 按文件名识别 manifest 类型。与 scan-service 的 ManifestParser.supports() 保持一致。
 */
public enum ManifestType {
    NPM_LOCKFILE,
    MAVEN_POM,
    MAVEN_TREE;

    public static ManifestType detect(String fileName) {
        if (fileName == null) throw ApiException.badRequest("Missing file name");
        String f = fileName.toLowerCase(Locale.ROOT);
        if (f.endsWith("package-lock.json") || f.endsWith("npm-shrinkwrap.json")) return NPM_LOCKFILE;
        if (f.endsWith("pom.xml")) return MAVEN_POM;
        if (f.contains("dependency-tree") || f.contains("deptree") || f.endsWith(".tree.txt") || f.endsWith("tree.txt")) {
            return MAVEN_TREE;
        }
        throw ApiException.badRequest("Unsupported manifest. Upload package-lock.json, pom.xml, "
                + "or the output of `mvn dependency:tree -DoutputFile=dependency-tree.txt`");
    }
}
