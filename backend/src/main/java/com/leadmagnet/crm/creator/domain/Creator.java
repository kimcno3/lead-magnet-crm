package com.leadmagnet.crm.creator.domain;

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

/**
 * 수익 배분(R/S) 협업 크리에이터(ADR 0014). 배포 링크가 크리에이터를 id로 참조하고,
 * 방문·신청의 크리에이터는 링크를 통해 유도한다. 보관(archive)하면 새 링크를 만들 수 없다.
 */
@Entity
@Table(name = "creators")
public class Creator {

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_HANDLE_LENGTH = 100;
    static final int MAX_MEMO_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String handle;

    @Column(columnDefinition = "text")
    private String memo;

    @Convert(converter = CreatorStatusConverter.class)
    @Column(nullable = false, length = 20)
    private CreatorStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Creator() {
    }

    private Creator(String name, String handle, String memo, Long createdBy, Instant now) {
        rejectControl(handle, memo);
        String newHandle = blankToNull(handle);
        String newMemo = blankToNull(memo);
        validate(name, newHandle, newMemo);
        this.name = name.trim();
        this.handle = newHandle;
        this.memo = newMemo;
        this.status = CreatorStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 핸들·메모는 앞뒤 공백을 지우고, 비어 있으면 null로 둔다. */
    public static Creator create(String name, String handle, String memo, Long createdBy, Instant now) {
        return new Creator(name, handle, memo, createdBy, now);
    }

    /**
     * null인 인자는 바꾸지 않는다. 핸들·메모에 빈 문자열을 주면 지운다.
     * 합친 결과가 규칙을 어기면 아무것도 바꾸지 않고 검증 실패를 던진다.
     */
    public void update(String name, String handle, String memo, Instant now) {
        rejectControl(handle, memo);
        String newName = name != null ? name : this.name;
        String newHandle = handle != null ? blankToNull(handle) : this.handle;
        String newMemo = memo != null ? blankToNull(memo) : this.memo;
        validate(newName, newHandle, newMemo);
        this.name = newName.trim();
        this.handle = newHandle;
        this.memo = newMemo;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (status == CreatorStatus.ARCHIVED) {
            throw new DomainException(ErrorCode.CREATOR_ALREADY_ARCHIVED);
        }
        status = CreatorStatus.ARCHIVED;
        updatedAt = now;
    }

    public boolean isArchived() {
        return status == CreatorStatus.ARCHIVED;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** trim()이 앞뒤 제어 문자를 지우므로 다듬기 전의 입력에서 먼저 막는다. */
    private static void rejectControl(String handle, String memo) {
        if (TextInput.containsForbiddenControl(handle)) {
            throw DomainException.validation("handle", "handle에는 제어 문자를 쓸 수 없습니다");
        }
        if (TextInput.containsForbiddenControl(memo)) {
            throw DomainException.validation("memo", "memo에는 제어 문자를 쓸 수 없습니다");
        }
    }

    private static void validate(String name, String handle, String memo) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME_LENGTH) {
            throw DomainException.validation("name", "name은 1~" + MAX_NAME_LENGTH + "자여야 합니다");
        }
        if (TextInput.containsForbiddenControl(name)) {
            throw DomainException.validation("name", "name에는 제어 문자를 쓸 수 없습니다");
        }
        if (handle != null && handle.length() > MAX_HANDLE_LENGTH) {
            throw DomainException.validation("handle", "handle은 " + MAX_HANDLE_LENGTH + "자 이하여야 합니다");
        }
        if (memo != null && memo.length() > MAX_MEMO_LENGTH) {
            throw DomainException.validation("memo", "memo는 " + MAX_MEMO_LENGTH + "자 이하여야 합니다");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHandle() {
        return handle;
    }

    public String getMemo() {
        return memo;
    }

    public CreatorStatus getStatus() {
        return status;
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
