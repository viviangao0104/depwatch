package com.depwatch.scan.vuln;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
  * CVSS v3.0 / v3.1 base score calculator.
 *
  * OSV's severity field only carries the vector string (e.g. CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H),
  * not the score, so we compute it ourselves. Formula from the FIRST specification:
 * https://www.first.org/cvss/v3.1/specification-document#7-1-Base-Metrics-Equations
 */
public final class CvssV3Calculator {

    private CvssV3Calculator() {
    }

    public static OptionalDouble baseScore(String vector) {
        if (vector == null || !vector.startsWith("CVSS:3.")) return OptionalDouble.empty();

        Map<String, String> m = new HashMap<>();
        for (String part : vector.split("/")) {
            int idx = part.indexOf(':');
            if (idx > 0) m.put(part.substring(0, idx), part.substring(idx + 1));
        }
        try {
            boolean scopeChanged = "C".equals(req(m, "S"));

            double av = switch (req(m, "AV")) {
                case "N" -> 0.85;
                case "A" -> 0.62;
                case "L" -> 0.55;
                case "P" -> 0.20;
                default -> throw new IllegalArgumentException("AV");
            };
            double ac = switch (req(m, "AC")) {
                case "L" -> 0.77;
                case "H" -> 0.44;
                default -> throw new IllegalArgumentException("AC");
            };
            double pr = switch (req(m, "PR")) {
                case "N" -> 0.85;
                case "L" -> scopeChanged ? 0.68 : 0.62;
                case "H" -> scopeChanged ? 0.50 : 0.27;
                default -> throw new IllegalArgumentException("PR");
            };
            double ui = switch (req(m, "UI")) {
                case "N" -> 0.85;
                case "R" -> 0.62;
                default -> throw new IllegalArgumentException("UI");
            };
            double c = cia(req(m, "C"));
            double i = cia(req(m, "I"));
            double a = cia(req(m, "A"));

            double iss = 1 - (1 - c) * (1 - i) * (1 - a);
            double impact = scopeChanged
                    ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15)
                    : 6.42 * iss;
            double exploitability = 8.22 * av * ac * pr * ui;

            if (impact <= 0) return OptionalDouble.of(0.0);
            double score = scopeChanged
                    ? roundUp(Math.min(1.08 * (impact + exploitability), 10))
                    : roundUp(Math.min(impact + exploitability, 10));
            return OptionalDouble.of(score);
        } catch (IllegalArgumentException e) {
            return OptionalDouble.empty();
        }
    }

    private static double cia(String v) {
        return switch (v) {
            case "H" -> 0.56;
            case "L" -> 0.22;
            case "N" -> 0.0;
            default -> throw new IllegalArgumentException("CIA");
        };
    }

    private static String req(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) throw new IllegalArgumentException(key);
        return v;
    }

    /**
      * Roundup as defined in the CVSS 3.1 spec: round up to one decimal using integer arithmetic to avoid floating-point error.
     */
    static double roundUp(double x) {
        long intInput = Math.round(x * 100_000);
        if (intInput % 10_000 == 0) {
            return intInput / 100_000.0;
        }
        return (Math.floorDiv(intInput, 10_000) + 1) / 10.0;
    }
}
