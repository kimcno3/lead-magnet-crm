package com.leadmagnet.crm.audit.application;

import java.util.Collection;
import java.util.Map;

/**
 * 감사 로그 화면에 운영자 이메일을 붙이기 위한 읽기 포트 (ADR 0016). 구현은 auth 패키지에 있다.
 * audit이 auth에 의존하지 않게(auth → audit 한 방향) 포트를 audit 쪽에 둔다.
 */
public interface OperatorDirectory {

    /** id → 이메일. 없는 id는 결과에 없다. */
    Map<Long, String> emailsOf(Collection<Long> operatorIds);
}
