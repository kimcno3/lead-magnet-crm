package com.leadmagnet.crm.common.crypto;

/**
 * 기동 시 HMAC 키 교체에 참여하는 쪽 (ADR 0022). 중복 판정 해시를 가진 도메인(신청)이 구현하고, {@link PiiKeyCheck}가
 * LEAD_HMAC_PREVIOUS_KEY가 설정된 채 기동하면 웹 서버가 요청을 받기 전에 부른다. common은 도메인 테이블을 모른다.
 */
public interface PiiHashRotation {

    /**
     * previous 키로 만든 해시를 current 키로 다시 계산해 바꾼다. 이미 current 키 해시인 행은 건너뛴다(중단 뒤 다시 불러도 안전).
     * 어느 키와도 맞지 않는 해시, 풀 수 없는 암호문, 같은 범위에서 새 해시가 겹치는 경우는 {@link PiiKeyRotationException}으로
     * 멈춘다. 이미 커밋한 배치는 그대로 둔다.
     *
     * @return 바꾼 행 수
     */
    long rotate(PiiHasher previous, PiiHasher current);
}
