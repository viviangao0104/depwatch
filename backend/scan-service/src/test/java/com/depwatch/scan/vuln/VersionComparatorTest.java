package com.depwatch.scan.vuln;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VersionComparatorTest {

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.1",
            "1.9.0, 1.10.0",          // numeric, not lexicographic
            "4.17.15, 4.17.21",
            "1.0.0-rc1, 1.0.0",       // pre-release < release
            "1.0.0-alpha, 1.0.0-beta",
            "2.17.0, 2.17.1",
            "5.3.30, 6.0.0",
            "1.0, 1.0.1",
            "v1.2.3, 1.2.4",
            "4.1.99.Final, 4.1.100.Final",
    })
    void ordersCorrectly(String lower, String higher) {
        assertThat(VersionComparator.INSTANCE.compare(lower, higher)).isNegative();
        assertThat(VersionComparator.INSTANCE.compare(higher, lower)).isPositive();
    }

    @ParameterizedTest
    @CsvSource({"1.0.0, 1.0.0", "1.0, 1.0.0", "v2.1, 2.1"})
    void equalVersions(String a, String b) {
        assertThat(VersionComparator.INSTANCE.compare(a, b)).isZero();
    }
}
