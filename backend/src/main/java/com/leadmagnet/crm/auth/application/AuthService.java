package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.auth.domain.RefreshToken;
import com.leadmagnet.crm.auth.domain.RefreshTokenRepository;
import com.leadmagnet.crm.common.PiiMasker;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 로그인, refresh token 교체·재사용 감지, 로그아웃 (ADR 0003).
 * refresh token 원문은 반환값으로만 넘기고 저장·로그에는 남기지 않는다.
 */
@Service
public class AuthService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final int REFRESH_TOKEN_BYTES = 32;
    // BCrypt는 72바이트까지만 비교하고 그보다 길면 예외를 던지므로 미리 거른다.
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OperatorRepository operators;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // 없는 이메일에도 같은 비용의 비교를 하기 위한 해시. 응답 시간으로 계정 존재를 알 수 없게 한다.
    private final String dummyPasswordHash;

    public AuthService(OperatorRepository operators, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer, Clock clock) {
        this.operators = operators;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public IssuedTokens login(String email, String password) {
        Optional<Operator> found = operators.findByEmail(Operator.normalizeEmail(email));
        if (!passwordMatches(password, found.map(Operator::getPasswordHash).orElse(dummyPasswordHash))
                || found.isEmpty()) {
            log.info("운영자 로그인 실패: {}", PiiMasker.maskEmail(email.trim()));
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS);
        }
        Operator operator = found.get();
        log.info("운영자 {} 로그인", operator.getId());
        return issue(operator, UUID.randomUUID());
    }

    /**
     * refresh token을 교체한다. 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 패밀리 전체를 폐기한다.
     * 재사용 감지 시 던지는 예외에서도 패밀리 폐기는 커밋되어야 하므로 DomainException은 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public IssuedTokens refresh(String rawRefreshToken) {
        RefreshToken current = findForUpdate(rawRefreshToken)
            .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));
        Instant now = clock.instant();
        if (current.isRevoked()) {
            int revoked = refreshTokens.revokeFamily(current.getFamilyId(), now);
            log.warn("refresh token 재사용 감지: 운영자 {} 패밀리 {} 의 토큰 {}개 폐기",
                current.getOperatorId(), current.getFamilyId(), revoked);
            throw new DomainException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (current.isExpired(now)) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        Operator operator = operators.findById(current.getOperatorId())
            .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));
        current.revoke(now);
        return issue(operator, current.getFamilyId());
    }

    /** 토큰이 없거나 모르는 토큰이어도 조용히 끝낸다. */
    @Transactional
    public void logout(String rawRefreshToken) {
        findForUpdate(rawRefreshToken)
            .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public CurrentOperator currentOperator(long operatorId) {
        return operators.findById(operatorId)
            .map(operator -> new CurrentOperator(operator.getId(), operator.getEmail()))
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED));
    }

    private IssuedTokens issue(Operator operator, UUID familyId) {
        String rawRefreshToken = newRefreshToken();
        refreshTokens.save(RefreshToken.issue(operator.getId(), rawRefreshToken, familyId, clock.instant(),
            REFRESH_TOKEN_TTL));
        return new IssuedTokens(accessTokenIssuer.issue(operator), AccessTokenIssuer.TTL.toSeconds(),
            rawRefreshToken);
    }

    private Optional<RefreshToken> findForUpdate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHashForUpdate(RefreshToken.hashOf(rawRefreshToken));
    }

    private boolean passwordMatches(String password, String hash) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            passwordEncoder.matches("", hash);
            return false;
        }
        return passwordEncoder.matches(password, hash);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
