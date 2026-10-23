package com.leadmagnet.crm.support;

import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 클래스패스에만 있는 컨트롤러. 실제 요청 경로로 에러 응답 형식을 확인하는 데 쓴다.
 * admin 체인 아래에 두어 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/admin/__test/errors")
public class ErrorProbeController {

    public static final String PII_IN_MESSAGE = "010-9876-5432 probe@example.com";

    @GetMapping("/unexpected")
    public String unexpected() {
        throw new IllegalStateException("예상하지 못한 오류 " + PII_IN_MESSAGE);
    }

    @GetMapping("/positive")
    public int positive(@RequestParam @Positive int value) {
        return value;
    }
}
