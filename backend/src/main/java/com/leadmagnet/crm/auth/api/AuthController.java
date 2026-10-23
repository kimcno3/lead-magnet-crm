package com.leadmagnet.crm.auth.api;

import com.leadmagnet.crm.auth.application.AuthService;
import com.leadmagnet.crm.auth.application.IssuedTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @Operation(operationId = "login", summary = "로그인", description = "access token은 바디로, refresh token은 HttpOnly 쿠키(lm_refresh)로 준다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "invalid_credentials: 이메일 또는 비밀번호가 틀림")
    @ApiResponse(responseCode = "422", description = "validation_failed: 요청 형식 오류")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request.email(), request.password()));
    }

    @Operation(operationId = "refreshToken", summary = "access token 재발급", description = "refresh 쿠키를 교체한다. 이미 쓴 토큰이 오면 그 로그인의 토큰을 모두 폐기한다.")
    @Parameter(name = RefreshTokenCookies.NAME, in = ParameterIn.COOKIE, description = "refresh token")
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "401", description = "invalid_refresh_token 또는 refresh_token_reused")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @Operation(operationId = "logout", summary = "로그아웃", description = "refresh 토큰 패밀리를 폐기하고 쿠키를 지운다. 쿠키가 없어도 204다.")
    @Parameter(name = RefreshTokenCookies.NAME, in = ParameterIn.COOKIE, description = "refresh token")
    @ApiResponse(responseCode = "204", description = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
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
