package com.leadmagnet.crm.common.crypto;

import java.util.List;

/**
 * 설정이 가진 비밀 키를 키 분리 검사({@link KeySeparationCheck})에 내보내는 포트. 각 설정(auth, form, 개인정보)이 자기
 * 키를 이 인터페이스로 노출해서, common이 도메인 설정 클래스를 알지 않고도 키가 겹치는지 볼 수 있다.
 * 구현체는 키 값을 로그나 메시지에 싣지 않는다.
 */
public interface SecretKeyMaterial {

    /** 오류·진단에 쓰는 환경 변수 이름(값이 아니다). */
    String name();

    /** 같은 비밀이 사용될 수 있는 바이트 표현들(원문 UTF-8 바이트, base64 해독 바이트 등). 복사본을 돌려준다. */
    List<byte[]> candidates();

    /** true이면 이 키는 다른 모든 비밀 키와 달라야 한다(예: JWT 서명 키가 데이터 암호화 키로 재사용되면 안 된다). */
    default boolean mustDifferFromOthers() {
        return false;
    }
}
