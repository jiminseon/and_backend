package com.example.user_module.common.config;

import com.example.common_service.security.JwtDecoderFactory;
import com.example.common_service.security.RsaKeyLoader;
import com.example.user_module.common.security.jwt.JwtAccessDeniedHandler;
import com.example.user_module.common.security.jwt.JwtAuthenticationEntryPoint;
import com.example.user_module.common.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtProperties jwtProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin) // H2 콘솔사용
        );

        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));// Session 미사용

        // httpBasic, httpFormLogin 비활성화
        http.httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        // Access tokens are verified locally with the public key; no user DB lookup is needed.
        http.exceptionHandling((exceptionHandling) ->
                exceptionHandling
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
        );

        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler));

        http.authorizeHttpRequests((authorize) ->
                        // Swagger UI 외부 접속 허용
                        authorize.requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // DefaultExceptionHandler 처리를 위한 error PermitAll
                        .requestMatchers("/error/**").permitAll()
                        // 이외의 모든 요청은 인증 정보 필요
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoderFactory.create(
                RsaKeyLoader.readPublicKey(jwtProperties.getPublicKey()),
                jwtProperties.getIssuer(),
                jwtProperties.getAudience());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
