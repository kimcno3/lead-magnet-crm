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

    @Column(nullable = false)
    private int size;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HtmlTemplate() {
    }

    private HtmlTemplate(String name, String content, List<FormField> fields, Long createdBy, Instant createdAt) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        this.name = name;
        this.content = content;
        this.sha256 = sha256Hex(bytes);
        this.fields = List.copyOf(fields);
        this.size = bytes.length;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static HtmlTemplate create(String name, String content, List<FormField> fields, Long createdBy,
            Instant createdAt) {
        return new HtmlTemplate(name, content, fields, createdBy, createdAt);
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

    public int getSize() {
        return size;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
