package com.leadmagnet.crm.template.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.template.domain.FormContractParser;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import com.leadmagnet.crm.template.domain.HtmlTemplateRepository;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
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

    public HtmlTemplate upload(String filename, byte[] bytes, String requestedName, long operatorId) {
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
        return templates.save(HtmlTemplate.create(name, content, fields, operatorId, clock.instant()));
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
        String name = requested == null || requested.isBlank() ? fromFile.trim() : requested.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            if (requested != null && !requested.isBlank()) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "name은 " + MAX_NAME_LENGTH + "자 이하여야 합니다");
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
