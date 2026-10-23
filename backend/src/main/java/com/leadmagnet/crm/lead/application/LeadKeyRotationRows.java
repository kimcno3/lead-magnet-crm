package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.lead.domain.ProtectedContact;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 키 교체(ADR 0022)가 읽고 쓰는 신청 행. 엔티티를 거치지 않고 암호문·해시 컬럼만 id keyset으로 다룬다(행마다 엔티티 전체를
 * 읽을 필요가 없고, 조건부 UPDATE로 동시 변경을 막는다). 호출자 트랜잭션에서 돈다. 익명화된 행(암호문·해시 NULL)은 대상이
 * 아니다.
 */
@Component
class LeadKeyRotationRows {

    private final JdbcTemplate jdbc;

    LeadKeyRotationRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 암호문의 키 id. 형식 1은 1, 형식 2는 둘째 바이트, 모르는 형식은 -1 ({@code PiiCipher.keyIdOf}와 같은 규칙).
     */
    static String keyIdSql(String column) {
        return "CASE WHEN length(" + column + ") < 2 THEN -1 WHEN get_byte(" + column + ", 0) = 1 THEN 1 "
            + "WHEN get_byte(" + column + ", 0) = 2 THEN get_byte(" + column + ", 1) ELSE -1 END";
    }

    private static String notOnKey(String column) {
        return "(" + column + " IS NOT NULL AND " + keyIdSql(column) + " <> ?)";
    }

    private static final String NOT_CURRENT = "(" + notOnKey("name_enc") + " OR " + notOnKey("phone_enc") + " OR "
        + notOnKey("email_enc") + ")";

    /** 현재 키 id가 아닌 암호문이 하나라도 있는 신청을 id 순으로 limit건. 행 잠금을 잡는다(파기와 겹치지 않게). */
    List<Row> findNotOnKey(int currentKeyId, long afterId, int limit) {
        return jdbc.query("SELECT id, form_id, name_enc, phone_enc, email_enc, phone_hash, email_hash FROM leads "
            + "WHERE id > ? AND anonymized_at IS NULL AND " + NOT_CURRENT + " ORDER BY id LIMIT ? FOR UPDATE",
            (rs, i) -> row(rs), afterId, currentKeyId, currentKeyId, currentKeyId, limit);
    }

    /** 현재 키 id가 아닌 암호문이 남은 신청 수. */
    long countNotOnKey(int currentKeyId) {
        return jdbc.queryForObject("SELECT count(*) FROM leads WHERE anonymized_at IS NULL AND " + NOT_CURRENT,
            Long.class, currentKeyId, currentKeyId, currentKeyId);
    }

    /** 해시가 있는 신청을 id 순으로 limit건. 행 잠금을 잡는다. */
    List<Row> findHashed(long afterId, int limit) {
        return jdbc.query("SELECT id, form_id, name_enc, phone_enc, email_enc, phone_hash, email_hash FROM leads "
            + "WHERE id > ? AND (phone_hash IS NOT NULL OR email_hash IS NOT NULL) ORDER BY id LIMIT ? FOR UPDATE",
            (rs, i) -> row(rs), afterId, limit);
    }

    /** 암호문을 바꾼다. 읽은 뒤 익명화된 행은 바꾸지 않는다(원문을 되살리지 않게). */
    boolean updateCiphertexts(long id, ProtectedContact contact) {
        return jdbc.update("UPDATE leads SET name_enc = ?, phone_enc = ?, email_enc = ? "
            + "WHERE id = ? AND anonymized_at IS NULL", contact.nameEnc(), contact.phoneEnc(), contact.emailEnc(), id) == 1;
    }

    /** 해시를 바꾼다. 읽은 값 그대로일 때만 바꾼다. */
    boolean updateHashes(Row row, ProtectedContact rehashed) {
        return jdbc.update("UPDATE leads SET phone_hash = ?, email_hash = ? WHERE id = ? "
            + "AND phone_hash IS NOT DISTINCT FROM ? AND email_hash IS NOT DISTINCT FROM ?",
            rehashed.phoneHash(), rehashed.emailHash(), row.id(), row.contact().phoneHash(),
            row.contact().emailHash()) == 1;
    }

    /** 같은 폼에서 이 해시를 이미 가진 다른 신청 id. 없으면 null. column은 phone_hash 또는 email_hash. */
    Long holderOf(String column, long formId, String hash, long exceptId) {
        if (hash == null) {
            return null;
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM leads WHERE form_id = ? AND " + column + " = ? AND id <> ? "
            + "LIMIT 1", Long.class, formId, hash, exceptId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getLong("form_id"), new ProtectedContact(rs.getBytes("name_enc"),
            rs.getBytes("phone_enc"), rs.getBytes("email_enc"), rs.getString("phone_hash"), rs.getString("email_hash")));
    }

    record Row(long id, long formId, ProtectedContact contact) {
    }
}
