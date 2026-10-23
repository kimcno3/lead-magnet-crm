package com.leadmagnet.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 패키지 의존 방향 규칙(v0.3.0 #55). 새 의존성(ArchUnit) 없이 main 소스에서 금지된 패키지 참조를 찾는다.
 * import뿐 아니라 완전한 이름({@code com.leadmagnet.crm.stats.…})으로 쓴 참조와 주석 속 {@code @link}도 같은 줄에서 잡힌다.
 * 규칙을 바꾸려면 CLAUDE.md의 구조 설명과 함께 고친다.
 */
class PackageDependencyRulesTest {

    static final String ROOT = "com.leadmagnet.crm";
    static final Path MAIN = Path.of("src/main/java", ROOT.replace('.', '/'));

    static final Set<String> DOMAINS =
        Set.of("auth", "template", "campaign", "form", "link", "tracking", "lead", "stats", "audit", "creator");

    /** 소스 패키지 → 참조하면 안 되는 패키지 */
    static final Map<String, Set<String>> FORBIDDEN = Map.of(
        "common", DOMAINS,
        "template", Set.of("form"),
        "audit", Set.of("auth", "lead", "campaign", "form", "template", "creator", "stats"),
        "lead", Set.of("stats"));

    private static final Pattern REFERENCE = Pattern.compile("\\b" + Pattern.quote(ROOT) + "\\.([a-z]+)\\b");

    /** source: 소스 파일이 속한 최상위 패키지 이름. 위반 설명을 모아 돌려준다. */
    static List<String> violations(String sourcePackage, String fileName, String text) {
        Set<String> forbidden = FORBIDDEN.getOrDefault(sourcePackage, Set.of());
        List<String> found = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("package ")) {
                continue;
            }
            Matcher m = REFERENCE.matcher(lines[i]);
            while (m.find()) {
                if (forbidden.contains(m.group(1))) {
                    found.add(fileName + ":" + (i + 1) + " " + sourcePackage + " → " + m.group(1));
                }
            }
        }
        return found;
    }

    @Test
    void main_소스는_금지된_패키지_의존을_갖지_않는다() throws IOException {
        List<String> all = new ArrayList<>();
        for (String pkg : FORBIDDEN.keySet()) {
            Path dir = MAIN.resolve(pkg);
            assertThat(dir).as("규칙의 대상 패키지가 있어야 한다: " + pkg).isDirectory();
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    all.addAll(violations(pkg, MAIN.relativize(file).toString(), Files.readString(file)));
                }
            }
        }
        assertThat(all).as("금지된 패키지 의존").isEmpty();
    }

    @Test
    void 스캐너는_금지된_import를_위반으로_보고한다() {
        String source = """
            package com.leadmagnet.crm.audit.application;

            import com.leadmagnet.crm.lead.domain.Lead;
            import com.leadmagnet.crm.common.Csv;

            class Fake {
                com.leadmagnet.crm.stats.domain.StatsView view;
            }
            """;

        assertThat(violations("audit", "audit/application/Fake.java", source)).containsExactly(
            "audit/application/Fake.java:3 audit → lead",
            "audit/application/Fake.java:7 audit → stats");
    }

    @Test
    void 스캐너는_허용된_의존과_자기_패키지는_위반으로_보지_않는다() {
        String source = """
            package com.leadmagnet.crm.template.application;

            import com.leadmagnet.crm.common.error.DomainException;
            import com.leadmagnet.crm.template.domain.HtmlTemplate;
            """;

        assertThat(violations("template", "template/application/Ok.java", source)).isEmpty();
        // common은 어떤 도메인도 참조할 수 없다
        assertThat(violations("common", "common/X.java", "import com.leadmagnet.crm.auth.config.AuthProperties;"))
            .containsExactly("common/X.java:1 common → auth");
        // template → form 금지
        assertThat(violations("template", "template/api/T.java", "import com.leadmagnet.crm.form.api.Foo;"))
            .containsExactly("template/api/T.java:1 template → form");
    }
}
