package com.leadmagnet.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void 쉼표_따옴표_줄바꿈이_있으면_따옴표로_감싸고_따옴표는_두_번_쓴다() {
        assertThat(Csv.line(Arrays.asList("a", "b,c", "say \"hi\"", "x\ny", null)))
            .isEqualTo("a,\"b,c\",\"say \"\"hi\"\"\",\"x\ny\",\r\n");
    }

    @Test
    void 수식으로_시작하는_값에는_작은따옴표를_붙인다() {
        assertThat(Csv.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(Csv.cell("+82")).isEqualTo("'+82");
        assertThat(Csv.cell("-5")).isEqualTo("'-5");
        assertThat(Csv.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.cell("\tcmd")).isEqualTo("'\tcmd");
        assertThat(Csv.cell("\rcmd")).isEqualTo("\"'\rcmd\"");
        assertThat(Csv.cell("=HYPERLINK(\"http://x\",\"y\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
    }

    @Test
    void 평범한_값과_빈_값은_그대로_둔다() {
        assertThat(Csv.cell("홍*동")).isEqualTo("홍*동");
        assertThat(Csv.cell("010-****-5678")).isEqualTo("010-****-5678");
        assertThat(Csv.cell("a=b")).isEqualTo("a=b");
        assertThat(Csv.cell("")).isEmpty();
        assertThat(Csv.cell(null)).isEmpty();
    }
}
