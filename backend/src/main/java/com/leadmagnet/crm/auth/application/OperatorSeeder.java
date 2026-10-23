package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.PiiMasker;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 환경 변수로 받은 운영자 계정을 기동 시 한 번 만든다.
 * 이미 있는 계정은 건드리지 않는다. 설정이 잘못되면 기동을 멈춘다.
 */
@Component
public class OperatorSeeder implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    // BCrypt는 72바이트 이후를 무시하므로 그보다 긴 비밀번호는 받지 않는다.
    static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(OperatorSeeder.class);

    private final OperatorSeedProperties properties;
    private final OperatorRepository operators;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public OperatorSeeder(OperatorSeedProperties properties, OperatorRepository operators,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.operators = operators;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    // 이메일 중복은 ux_operators_email 유니크 인덱스가 최종으로 막는다.
    public SeedResult seed() {
        if (properties.isEmpty()) {
            log.info("운영자 시드 설정이 없어 건너뜀");
            return SeedResult.SKIPPED;
        }
        validate(properties);

        String email = Operator.normalizeEmail(properties.email());
        if (operators.existsByEmail(email)) {
            log.info("운영자 {} 가 이미 있어 시드를 건너뜀", PiiMasker.maskEmail(email));
            return SeedResult.ALREADY_EXISTS;
        }
        operators.save(new Operator(email, passwordEncoder.encode(properties.password()), clock.instant()));
        log.info("운영자 {} 를 시드로 생성", PiiMasker.maskEmail(email));
        return SeedResult.CREATED;
    }

    private static void validate(OperatorSeedProperties p) {
        if (OperatorSeedProperties.isBlank(p.email()) || OperatorSeedProperties.isBlank(p.password())) {
            throw new InvalidSeedConfigurationException(
                "ADMIN_SEED_EMAIL과 ADMIN_SEED_PASSWORD는 함께 설정해야 합니다");
        }
        String email = p.email().trim();
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1 || email.length() > 254) {
            throw new InvalidSeedConfigurationException("ADMIN_SEED_EMAIL 형식이 올바르지 않습니다");
        }
        if (p.password().length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidSeedConfigurationException(
                "ADMIN_SEED_PASSWORD는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다");
        }
        if (p.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidSeedConfigurationException(
                "ADMIN_SEED_PASSWORD는 UTF-8 기준 " + MAX_PASSWORD_BYTES + "바이트 이하여야 합니다");
        }
    }

    public enum SeedResult {
        SKIPPED, CREATED, ALREADY_EXISTS
    }

    public static class InvalidSeedConfigurationException extends IllegalStateException {
        public InvalidSeedConfigurationException(String message) {
            super(message);
        }
    }
}
