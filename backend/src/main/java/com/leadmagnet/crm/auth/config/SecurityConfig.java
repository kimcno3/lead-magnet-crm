package com.leadmagnet.crm.auth.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 보안 필터 체인 두 개 (ADR 0003).
 * admin 체인은 /api/admin/** 를 Bearer JWT로 보호한다. 기본 체인은 문서·헬스·공개 경로만 열고 나머지는 막는다.
 */
@Configuration(proxyBeanMethods = false)
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler,
                                   AuthProperties properties) throws Exception {
        http.securityMatcher("/api/admin/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                    "/api/admin/auth/login", "/api/admin/auth/refresh", "/api/admin/auth/logout").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(problemHandler)
                .accessDeniedHandler(problemHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 인증은 Authorization 헤더로만 한다. 쿠키를 쓰는 refresh·logout은 SameSite=Strict와
            // Path=/api/admin/auth 쿠키로 교차 사이트 요청에 실리지 않으므로 CSRF 토큰을 두지 않는다.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(adminCors(properties)))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // 공개 경로는 이슈 #7에서 public 체인으로 분리한다.
                .requestMatchers("/f/**", "/l/**", "/api/public/**").permitAll()
                // 컨트롤러 밖에서 난 오류를 Boot 기본 오류 응답으로 내보내기 위한 경로
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 이 체인에는 쿠키 기반 인증이 없다.
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }

    static CorsConfigurationSource adminCors(AuthProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.adminOrigin()));
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", config);
        return source;
    }
}
