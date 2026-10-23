package com.leadmagnet.crm.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 테스트는 컨테이너 하나를 공유하므로 각 테스트 전에 업무 테이블을 비운다. */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void truncateAll(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE operators, refresh_tokens, html_templates, campaigns, forms, "
            + "creators, distribution_links, visits, leads RESTART IDENTITY CASCADE");
    }
}
