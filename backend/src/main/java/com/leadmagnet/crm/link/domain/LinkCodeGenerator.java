package com.leadmagnet.crm.link.domain;

/** 링크 코드 생성 지점. 운영은 무작위 base62, 테스트에서는 충돌을 일으키도록 바꿔 끼운다. */
public interface LinkCodeGenerator {

    String next();
}
