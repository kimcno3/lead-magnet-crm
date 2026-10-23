package com.leadmagnet.crm.template.application;

import com.leadmagnet.crm.common.TextInput;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.template.domain.FormContractParser;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import com.leadmagnet.crm.template.domain.HtmlTemplateRepository;
import com.leadmagnet.crm.template.domain.InspectionWarning;
import com.leadmagnet.crm.template.domain.TemplateInspector;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TemplateService {

    static final int MAX_NAME_LENGTH = 100;

    private final HtmlTemplateRepository templates;
    private final TemplateProperties properties;
    private final Clock clock;

    public TemplateService(HtmlTemplateRepository templates, TemplateProperties properties, Clock clock) {
        this.templates = templates;
        this.properties = properties;
        this.clock = clock;
    }

    /** 업로드 파일 검증 결과. 저장하지 않는 "검사만 하기"와 업로드, 새 버전 업로드(#17)가 함께 쓴다. */
    public record Inspected(String name, String content, List<FormField> fields, List<InspectionWarning> inspection) {
    }

    public HtmlTemplate upload(String filename, byte[] bytes, String requestedName, long operatorId) {
        Inspected checked = inspect(filename, bytes, requestedName);
        return templates.save(HtmlTemplate.create(checked.name(), checked.content(), checked.fields(),
            checked.inspection(), operatorId, clock.instant()));
    }

    /**
     * 파일 계약(확장자·크기·UTF-8·폼 계약)을 검사하고 경고를 계산한다. 아무것도 저장하지 않는다.
     * 계약을 어기면 업로드와 같은 DomainException(422·413)을 던진다.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Inspected inspect(String filename, byte[] bytes, String requestedName) {
        String baseName = baseName(filename);
        String lower = baseName.toLowerCase(Locale.ROOT);
        boolean html = lower.endsWith(".html");
        if (!html && !lower.endsWith(".htm")) {
            throw new DomainException(ErrorCode.TEMPLATE_INVALID_EXTENSION);
        }
        if (bytes.length == 0) {
            throw new DomainException(ErrorCode.TEMPLATE_EMPTY);
        }
        if (bytes.length > properties.maxBytes()) {
            throw new DomainException(ErrorCode.TEMPLATE_TOO_LARGE);
        }
        String content = decodeUtf8(bytes);
        if (content.isBlank()) {
            throw new DomainException(ErrorCode.TEMPLATE_EMPTY);
        }
        String name = resolveName(requestedName, baseName.substring(0, baseName.lastIndexOf('.')));

        List<FormField> fields = FormContractParser.parse(content);
        return new Inspected(name, content, fields, TemplateInspector.inspect(content));
    }

    /** 저장된 검사 결과. 기존 템플릿(null)은 지금 계산해 돌려주고 저장하지는 않는다(ADR 0017). */
    public List<InspectionWarning> inspectionOf(HtmlTemplate template) {
        List<InspectionWarning> stored = template.getInspection();
        return stored != null ? stored : TemplateInspector.inspect(template.getContent());
    }

    @Transactional(readOnly = true)
    public List<HtmlTemplate> list() {
        return templates.findAllByOrderByCreatedAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public HtmlTemplate get(long id) {
        return templates.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private static String baseName(String filename) {
        String value = filename == null ? "" : filename;
        return value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')) + 1);
    }

    private static String resolveName(String requested, String fromFile) {
        String raw = requested == null || requested.isBlank() ? fromFile : requested;
        if (TextInput.containsForbiddenControl(raw)) {
            throw DomainException.validation("name", "name에는 제어 문자를 쓸 수 없습니다");
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            if (requested != null && !requested.isBlank()) {
                throw DomainException.validation("name", "name은 " + MAX_NAME_LENGTH + "자 이하여야 합니다");
            }
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return name.isEmpty() ? "template" : name;
    }

    /** UTF-8 외 바이트나 NUL(PostgreSQL text에 저장 불가)이 있으면 거부한다. UTF-8 BOM은 제거한다. */
    private static String decodeUtf8(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
            && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                .toString();
            if (text.indexOf('\u0000') >= 0) {
                throw new DomainException(ErrorCode.TEMPLATE_NOT_UTF8);
            }
            return text;
        } catch (CharacterCodingException e) {
            throw new DomainException(ErrorCode.TEMPLATE_NOT_UTF8);
        }
    }
}
