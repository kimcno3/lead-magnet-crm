package com.leadmagnet.crm.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.template.domain.FormContractParser;
import com.leadmagnet.crm.template.domain.TemplateInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** docs/AI_FORM_PROMPT.md의 예시 출력이 실제로 오류도 경고도 없는지, 대응표가 검사 코드를 빠짐없이 담았는지 확인한다. */
class AiFormPromptDocTest {

    private static final Path DOC = Path.of("..", "docs", "AI_FORM_PROMPT.md");
    private static final Path TROUBLESHOOTING = Path.of("..", "docs", "TROUBLESHOOTING.md");
    private static final List<String> WARNING_CODES = List.of("external_script", "external_stylesheet", "external_image",
        "external_font", "form_action_external", "fetch_in_script", "missing_label", "contact_not_required",
        "extra_contact_field", "consent_in_html", "large_inline_image");

    private static String block(String markdown, String lang) {
        Matcher m = Pattern.compile("```" + lang + "\\n(.*?)\\n```", Pattern.DOTALL).matcher(markdown);
        assertThat(m.find()).as("%s 코드 블록", lang).isTrue();
        return m.group(1);
    }

    @Test
    void 예시_출력은_계약_오류도_검사_경고도_없다() throws IOException {
        String sample = block(Files.readString(DOC), "html");

        assertThat(FormContractParser.parse(sample)).isNotEmpty();
        assertThat(TemplateInspector.inspect(sample)).isEmpty();
    }

    @Test
    void 관리자_화면의_프롬프트_상수는_문서의_프롬프트와_같다() throws IOException {
        String path = System.getProperty("aiformprompt.ts");
        assertThat(path).as("Gradle이 aiformprompt.ts를 넘긴다(IDE 실행이면 -Daiformprompt.ts=<경로>)").isNotBlank();
        String ts = Files.readString(Path.of(path));
        int start = ts.indexOf("= `") + 3;
        int end = ts.lastIndexOf('`');
        assertThat(start).isGreaterThan(2);

        assertThat(ts.substring(start, end)).isEqualTo(block(Files.readString(DOC), "text"));
    }

    @Test
    void 대응표와_문제_해결_문서는_모든_경고_코드를_설명한다() throws IOException {
        String doc = Files.readString(DOC);
        String troubleshooting = Files.readString(TROUBLESHOOTING);

        for (String code : WARNING_CODES) {
            assertThat(doc).as("AI_FORM_PROMPT에 %s", code).contains("`" + code + "`");
            assertThat(troubleshooting).as("TROUBLESHOOTING에 %s", code).contains("`" + code + "`");
        }
    }
}
