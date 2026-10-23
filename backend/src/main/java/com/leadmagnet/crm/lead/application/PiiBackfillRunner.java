package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.lead.domain.ProtectedContact;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * expand 단계 백필 (ADR 0015). 기동 시 평문만 있고 암호문이 비어 있는 신청 행을 배치로 암호화·해시해 채운다.
 * 이미 채운 컬럼은 건드리지 않으므로 여러 번 돌려도 같다(멱등). 배치마다 따로 커밋하고 id 순으로 앞으로만 간다.
 * 로그에는 건수만 남긴다. 키가 DB 밖(환경 변수)에 있어 Flyway SQL로는 할 수 없다.
 */
@Component
public class PiiBackfillRunner implements ApplicationRunner {

    static final int BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(PiiBackfillRunner.class);

    private static final String SELECT = """
        SELECT id, name, phone, email FROM leads
        WHERE id > ?
          AND ((name_enc IS NULL AND name IS NOT NULL)
            OR (phone_enc IS NULL AND phone IS NOT NULL)
            OR (email_enc IS NULL AND email IS NOT NULL))
        ORDER BY id
        LIMIT ?
        """;

    // 이미 채운 컬럼은 COALESCE로 그대로 둔다(동시에 들어온 새 신청이나 앞선 백필의 값을 덮지 않는다).
    private static final String UPDATE = """
        UPDATE leads SET name_enc = COALESCE(name_enc, ?), phone_enc = COALESCE(phone_enc, ?),
                         email_enc = COALESCE(email_enc, ?), phone_hash = COALESCE(phone_hash, ?),
                         email_hash = COALESCE(email_hash, ?)
        WHERE id = ?
        """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ContactProtector protector;

    public PiiBackfillRunner(JdbcTemplate jdbc, TransactionTemplate tx, ContactProtector protector) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.protector = protector;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill(BATCH_SIZE);
    }

    /** 채운 행 수를 돌려준다. */
    public int backfill(int batchSize) {
        long lastId = 0;
        int total = 0;
        int batches = 0;
        while (true) {
            List<Row> rows = jdbc.query(SELECT, (rs, i) -> new Row(rs.getLong("id"), rs.getString("name"),
                rs.getString("phone"), rs.getString("email")), lastId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            tx.executeWithoutResult(status -> rows.forEach(this::fill));
            total += rows.size();
            batches++;
            lastId = rows.getLast().id();
        }
        if (total > 0) {
            log.info("개인정보 백필 완료: {}건 ({}배치)", total, batches);
        } else {
            log.info("개인정보 백필: 채울 행 없음");
        }
        return total;
    }

    private void fill(Row row) {
        ProtectedContact c = protector.protect(row.name(), row.phone(), row.email());
        jdbc.update(UPDATE, c.nameEnc(), c.phoneEnc(), c.emailEnc(), c.phoneHash(), c.emailHash(), row.id());
    }

    private record Row(long id, String name, String phone, String email) {
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
