package com.depwatch.scan.vuln;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CvssV3CalculatorTest {

    /** 期望值来自 NVD 官方计算器。 */
    @ParameterizedTest
    @CsvSource({
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H, 9.8",   // Log4Shell 级别
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H, 10.0",
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N, 6.1",   // 典型 XSS
            "CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N, 6.5",
            "CVSS:3.1/AV:L/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N, 1.8",
            "CVSS:3.0/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:N/A:H, 5.9",   // ReDoS 常见
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N, 0.0",
    })
    void matchesOfficialCalculator(String vector, double expected) {
        assertThat(CvssV3Calculator.baseScore(vector)).hasValue(expected);
    }

    @ParameterizedTest
    @CsvSource({"4.00, 4.0", "4.02, 4.1", "4.00001, 4.1", "9.95, 10.0"})
    void roundUpFollowsSpec(double in, double expected) {
        assertThat(CvssV3Calculator.roundUp(in)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N", "garbage", "CVSS:3.1/AV:X"})
    void unsupportedVectorsAreEmpty(String vector) {
        assertThat(CvssV3Calculator.baseScore(vector)).isEmpty();
    }
}
