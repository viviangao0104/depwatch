package com.depwatch.project.project;

import com.depwatch.common.web.ApiException;

import java.util.Locale;

/**
  * Detects the manifest type from the file name. Keep in sync with ManifestParser.supports() in scan-service.
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
