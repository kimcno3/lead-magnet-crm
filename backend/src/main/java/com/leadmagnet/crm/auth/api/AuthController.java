package com.leadmagnet.crm.auth.api;

import com.leadmagnet.crm.audit.api.AuditActors;
import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.auth.application.AuthService;
import com.leadmagnet.crm.auth.application.IssuedTokens;
import com.leadmagnet.crm.auth.application.LoginThrottle;
import com.leadmagnet.crm.auth.application.RefreshTokenReusedException;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "운영자 로그인과 토큰 교체 (ADR 0003)")
@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;
    private final AuditService audit;
    private final LoginThrottle loginThrottle;

    public AuthController(AuthService authService, RefreshTokenCookies cookies, AuditService audit,
            LoginThrottle loginThrottle) {
        this.authService = authService;
        this.cookies = cookies;
        this.audit = audit;
        this.loginThrottle = loginThrottle;
    }

    @Operation(operationId = "login", summary = "로그인", description = "access token은 바디로, refresh token은 HttpOnly 쿠키(lm_refresh)로 준다. 성공·실패는 감사 로그에 남는다(ADR 0016). (이메일, IP) 쌍·이메일 전체·IP별 실패가 창(기본 15분) 안에서 한도(기본 5회·50회·20회)에 닿으면 비밀번호를 확인하지 않고 429를 준다. 없는 이메일도 같은 규칙이다(ADR 0021).")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "invalid_credentials: 이메일 또는 비밀번호가 틀림",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "validation_failed: 요청 형식 오류",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "too_many_login_attempts: 로그인 시도 제한. Retry-After 헤더(초) 뒤에 다시 시도한다",
        headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "다시 시도할 수 있을 때까지의 초", schema = @Schema(type = "integer")),
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        // 감사 기록은 로그인 트랜잭션이 끝난 뒤 남긴다. 기록이 실패해도 로그인 결과는 바뀌지 않는다(ADR 0016).
        // IP는 ClientIpFilter가 정한 getRemoteAddr()이고, 감사 기록과 시도 제한이 같은 값을 쓴다(ADR 0021).
        AuditActor actor = AuditActors.anonymous(httpRequest);
        LoginThrottle.Attempt attempt = loginThrottle.begin(request.email(), actor.ip());
        IssuedTokens tokens;
        try {
            tokens = authService.login(request.email(), request.password());
        } catch (DomainException e) {
            if (e.getErrorCode() != ErrorCode.INVALID_CREDENTIALS) {
                attempt.abandoned();
                throw e;
            }
            List<String> locked = attempt.failed();
            audit.recordBestEffort(actor, AuditEvent.loginFailure(request.email()));
            if (!locked.isEmpty()) {
                audit.recordBestEffort(actor,
                    AuditEvent.loginLocked(request.email(), locked, loginThrottle.windowMinutes()));
            }
            throw e;
        } catch (RuntimeException e) {
            attempt.abandoned();
            throw e;
        }
        attempt.succeeded();
        audit.recordBestEffort(actor.withOperator(tokens.operatorId()), AuditEvent.loginSuccess(tokens.operatorId()));
        return withRefreshCookie(tokens);
    }

    @Operation(operationId = "refreshToken", summary = "access token 재발급", description = "refresh 쿠키를 교체한다. 이미 쓴 토큰이 오면 그 로그인의 토큰을 모두 폐기한다.")
    @Parameter(name = RefreshTokenCookies.NAME, in = ParameterIn.COOKIE, description = "refresh token")
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "401", description = "invalid_refresh_token 또는 refresh_token_reused")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        try {
            return withRefreshCookie(authService.refresh(refreshToken));
        } catch (RefreshTokenReusedException e) {
            // 패밀리 폐기는 noRollbackFor로 이미 커밋되었다. 기록은 그 뒤 트랜잭션 밖에서 남기고 실패해도 401은 그대로다(ADR 0016).
            if (e.revokedLiveTokens()) {
                audit.recordBestEffort(AuditActors.anonymous(httpRequest).withOperator(e.getOperatorId()),
                    AuditEvent.refreshReused(e.getOperatorId()));
            }
            throw e;
        }
    }

    @Operation(operationId = "logout", summary = "로그아웃", description = "refresh 토큰 패밀리를 폐기하고 쿠키를 지운다. 쿠키가 없어도 204다.")
    @Parameter(name = RefreshTokenCookies.NAME, in = ParameterIn.COOKIE, description = "refresh token")
    @ApiResponse(responseCode = "204", description = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        authService.logout(refreshToken).ifPresent(operatorId -> audit.recordBestEffort(
            AuditActors.anonymous(httpRequest).withOperator(operatorId), AuditEvent.logout(operatorId)));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @Operation(operationId = "getCurrentOperator", summary = "현재 운영자", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "현재 운영자")
    @ApiResponse(responseCode = "401", description = "unauthorized: 토큰이 없거나 유효하지 않음")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(authService.currentOperator(Long.parseLong(jwt.getSubject())));
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.refreshToken()).toString())
            .body(TokenResponse.from(tokens));
    }
}
