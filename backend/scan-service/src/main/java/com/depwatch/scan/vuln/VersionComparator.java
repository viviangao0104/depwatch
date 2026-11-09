package com.depwatch.scan.vuln;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * 宽松的版本比较器，同时适用于 semver（npm）和 Maven 风格版本。
 *
 * 规则：按 . - + 切段；两段都是数字按数值比，否则按字符串比；
 * 带预发布后缀（1.0.0-rc1）小于同核心的正式版（1.0.0）；
 * 常见限定词排序 alpha < beta < milestone < rc < snapshot < 正式 < sp。
 *
 * 这不是完整的 Maven ComparableVersion，但对"找到比当前版本大的最小修复版本"足够。
 */
public final class VersionComparator implements Comparator<String> {

    public static final VersionComparator INSTANCE = new VersionComparator();
    private static final Pattern SPLIT = Pattern.compile("[.\\-+_]");

    private VersionComparator() {
    }

    @Override
    public int compare(String a, String b) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;
        String[] pa = SPLIT.split(stripPrefix(a));
        String[] pb = SPLIT.split(stripPrefix(b));
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String x = i < pa.length ? pa[i] : null;
            String y = i < pb.length ? pb[i] : null;
            int c = compareSegment(x, y);
            if (c != 0) return c;
        }
        return 0;
    }

    private static String stripPrefix(String v) {
        String s = v.trim();
        return s.startsWith("v") || s.startsWith("V") ? s.substring(1) : s;
    }

    private static int compareSegment(String x, String y) {
        // 缺失段：数字缺失视为 0；若另一边是限定词（预发布），缺失（正式版）更大
        if (x == null) return y == null ? 0 : (isNumeric(y) ? compareNumeric("0", y) : 1);
        if (y == null) return isNumeric(x) ? compareNumeric(x, "0") : -1;

        boolean nx = isNumeric(x), ny = isNumeric(y);
        if (nx && ny) return compareNumeric(x, y);
        if (nx) return 1;        // 数字 > 限定词 (1.0.1 > 1.0-rc)
        if (ny) return -1;
        return Integer.compare(qualifierRank(x), qualifierRank(y)) != 0
                ? Integer.compare(qualifierRank(x), qualifierRank(y))
                : x.compareToIgnoreCase(y);
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static int compareNumeric(String x, String y) {
        String a = x.replaceFirst("^0+(?=\\d)", "");
        String b = y.replaceFirst("^0+(?=\\d)", "");
        if (a.length() != b.length()) return Integer.compare(a.length(), b.length());
        return a.compareTo(b);
    }

    private static int qualifierRank(String q) {
        String s = q.toLowerCase();
        if (s.startsWith("alpha") || s.equals("a")) return 1;
        if (s.startsWith("beta") || s.equals("b")) return 2;
        if (s.startsWith("milestone") || s.startsWith("m")) return 3;
        if (s.startsWith("rc") || s.startsWith("cr")) return 4;
        if (s.equals("snapshot")) return 5;
        if (s.equals("final") || s.equals("ga") || s.equals("release")) return 6;
        if (s.startsWith("sp")) return 7;
        return 0;
    }
}
