package com.leadmagnet.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.template.domain.FormContractParser;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.TemplateInspector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 예시 데이터 템플릿(ADR 0023)은 폼 계약(ADR 0004)을 지키고 업로드 검사(ADR 0017)에서 아무것도 나오지 않아야 한다. */
class DemoTemplatesTest {

    static final Path DEMO = Path.of("src/main/resources/demo");

    static List<Path> templates() throws IOException {
        try (Stream<Path> files = Files.list(DEMO)) {
            return files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
    }

    @Test
    void 예시_템플릿은_세_개_이상이고_시더가_쓰는_파일이_모두_있다() throws IOException {
        List<String> names = templates().stream().map(p -> p.getFileName().toString()).toList();

        assertThat(names).hasSizeGreaterThanOrEqualTo(3);
        assertThat(names).containsAll(DemoCatalog.templateFiles());
    }

    @Test
    void 예시_템플릿은_폼_계약을_지키고_검사_결과가_비어_있다() throws IOException {
        for (Path file : templates()) {
            String html = Files.readString(file, StandardCharsets.UTF_8);

            List<FormField> fields = FormContractParser.parse(html);
            assertThat(TemplateInspector.inspect(html)).as(file.getFileName() + " 검사 결과").isEmpty();
            assertThat(fields.stream().filter(f -> f.name().equals("phone") || f.name().equals("email")))
                .as(file.getFileName() + " 연락처 필드").isNotEmpty().anyMatch(FormField::required);
            assertThat(fields).as(file.getFileName() + " 라벨").allMatch(f -> !f.label().equals(f.name()));
        }
    }
}
