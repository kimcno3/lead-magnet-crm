package com.leadmagnet.crm.campaign.domain;

import com.leadmagnet.crm.common.TextInput;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 마케팅 캠페인. 폼을 묶는 단위이며 보관(archive)하면 새 폼을 만들 수 없다. */
@Entity
@Table(name = "campaigns")
public class Campaign {

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_DESCRIPTION_LENGTH = 2000;
    /** 신청 보존 기간(일) 기본값과 범위 (ADR 0020). DB CHECK ck_campaigns_retention_days와 같다. */
    public static final int DEFAULT_RETENTION_DAYS = 365;
    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 3650;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Convert(converter = CampaignStatusConverter.class)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Campaign() {
    }

    private Campaign(String name, String description, Instant startsAt, Instant endsAt, Integer retentionDays,
            Long createdBy, Instant now) {
        validate(name, description, startsAt, endsAt);
        int days = retentionDays != null ? retentionDays : DEFAULT_RETENTION_DAYS;
        validateRetention(days);
        this.retentionDays = days;
        this.name = name.trim();
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = CampaignStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** retentionDays가 null이면 {@value #DEFAULT_RETENTION_DAYS}일이다. */
    public static Campaign create(String name, String description, Instant startsAt, Instant endsAt,
            Integer retentionDays, Long createdBy, Instant now) {
        return new Campaign(name, description, startsAt, endsAt, retentionDays, createdBy, now);
    }

    /** null인 인자는 바꾸지 않는다. 합친 결과가 규칙을 어기면 아무것도 바꾸지 않고 검증 실패를 던진다. */
    public void update(String name, String description, Instant startsAt, Instant endsAt, Integer retentionDays,
            Instant now) {
        String newName = name != null ? name : this.name;
        String newDescription = description != null ? description : this.description;
        Instant newStartsAt = startsAt != null ? startsAt : this.startsAt;
        Instant newEndsAt = endsAt != null ? endsAt : this.endsAt;
        int newRetentionDays = retentionDays != null ? retentionDays : this.retentionDays;
        validate(newName, newDescription, newStartsAt, newEndsAt);
        validateRetention(newRetentionDays);
        this.retentionDays = newRetentionDays;
        this.name = newName.trim();
        this.description = newDescription;
        this.startsAt = newStartsAt;
        this.endsAt = newEndsAt;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (status == CampaignStatus.ARCHIVED) {
            throw new DomainException(ErrorCode.CAMPAIGN_ALREADY_ARCHIVED);
        }
        status = CampaignStatus.ARCHIVED;
        updatedAt = now;
    }

    public boolean isArchived() {
        return status == CampaignStatus.ARCHIVED;
    }

    private static void validate(String name, String description, Instant startsAt, Instant endsAt) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME_LENGTH) {
            throw invalid("name", "name은 1~" + MAX_NAME_LENGTH + "자여야 합니다");
        }
        if (TextInput.containsForbiddenControl(name)) {
            throw invalid("name", "name에는 제어 문자를 쓸 수 없습니다");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("description", "description은 " + MAX_DESCRIPTION_LENGTH + "자 이하여야 합니다");
        }
        if (TextInput.containsForbiddenControl(description)) {
            throw invalid("description", "description에는 제어 문자를 쓸 수 없습니다");
        }
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw invalid("startsAt", "startsAt은 endsAt보다 빨라야 합니다");
        }
    }

    private static void validateRetention(int days) {
        if (days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
            throw invalid("retentionDays",
                "retentionDays는 " + MIN_RETENTION_DAYS + "~" + MAX_RETENTION_DAYS + "일이어야 합니다");
        }
    }

    private static DomainException invalid(String field, String detail) {
        return DomainException.validation(field, detail);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    /** 신청 보존 기간(일). 신청 시각에서 이만큼(일 × 24시간)이 지나면 익명화한다(ADR 0020). */
    public int getRetentionDays() {
        return retentionDays;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
