package com.leadmagnet.crm.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 운영자가 올린 폼 HTML. 만들 때 크기와 sha256을 content에서 계산한다. */
@Entity
@Table(name = "html_templates")
public class HtmlTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String sha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<FormField> fields;

    /** 업로드 시점의 검사 경고. null이면 아직 계산하지 않은 기존 행이다(ADR 0017). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<InspectionWarning> inspection;

    @Column(nullable = false)
    private int size;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    /** 새 버전을 올릴 때마다 1씩 오른다 (ADR 0018). */
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HtmlTemplate() {
    }

    private HtmlTemplate(String name, String content, List<FormField> fields, List<InspectionWarning> inspection,
            Long createdBy, Instant createdAt) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.version = 1;
        applyContent(content, fields, inspection, createdAt);
    }

    private void applyContent(String content, List<FormField> fields, List<InspectionWarning> inspection, Instant now) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        this.content = content;
        this.sha256 = sha256Hex(bytes);
        this.fields = List.copyOf(fields);
        this.inspection = List.copyOf(inspection);
        this.size = bytes.length;
        this.updatedAt = now;
    }

    /** 새 버전: 내용·sha256·필드·크기·검사 결과를 바꾸고 버전을 올린다. 이름과 만든 사람은 그대로다. */
    public void newVersion(String content, List<FormField> fields, List<InspectionWarning> inspection, Instant now) {
        applyContent(content, fields, inspection, now);
        this.version++;
    }

    public static HtmlTemplate create(String name, String content, List<FormField> fields,
            List<InspectionWarning> inspection, Long createdBy, Instant createdAt) {
        return new HtmlTemplate(name, content, fields, inspection, createdBy, createdAt);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public String getSha256() {
        return sha256;
    }

    public List<FormField> getFields() {
        return fields;
    }

    /** 저장된 검사 경고. 기존 행은 null이다. 화면에는 {@code TemplateService.inspectionOf}로 얻는다. */
    public List<InspectionWarning> getInspection() {
        return inspection;
    }

    public int getSize() {
        return size;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
