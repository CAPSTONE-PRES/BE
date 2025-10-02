package com.pres.pres_server.config;

import com.pres.pres_server.config.oauth.OAuth2UserCustomService;
import com.pres.pres_server.config.oauth.OAuth2AuthorizationRequestBasedOnCookieRepository;
import com.pres.pres_server.config.oauth.OAuth2SuccessHandler;
import com.pres.pres_server.repository.RefreshTokenRepository;
import com.pres.pres_server.security.jwt.TokenAuthenticationFilter;
import com.pres.pres_server.security.jwt.TokenProvider;
import com.pres.pres_server.service.user.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Configuration
public class WebOAuthSecurityConfig {

        private final OAuth2UserCustomService customService;
        private final UserService userService;
        private final TokenProvider tokenProvider;
        private final RefreshTokenRepository refreshTokenRepository;

        @Bean
        public WebSecurityCustomizer configure() { // 스프링 시큐리티 기능 비활성화
                return (web) -> web.ignoring().requestMatchers("/h2-console/**")
                                .requestMatchers("/static/**");
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .csrf(csrf -> csrf.disable())
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .logout(AbstractHttpConfigurer::disable)
                                .sessionManagement(management -> management
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .addFilterBefore(tokenAuthenticationFilter(),
                                                UsernamePasswordAuthenticationFilter.class)
                                .authorizeHttpRequests(auth -> auth
                                                // Swagger UI와 OpenAPI JSON 인증 없이 접근 가능
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                                // 토큰 관련 API (테스트용 포함)
                                                .requestMatchers("/api/token", "/test-token").permitAll()
                                                // 인증 관련 API (회원가입, 로그인, 이메일 인증)
                                                .requestMatchers("/auth/**").permitAll()
                                                // 나머지 모든 API는 인증 필요
                                                .requestMatchers("/api/**", "/user/**", "/projects/**", "/workspace/**")
                                                .authenticated()
                                                .anyRequest().permitAll())
                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage("/login")
                                                .authorizationEndpoint(authorization -> authorization
                                                                .authorizationRequestRepository(
                                                                                oAuth2AuthorizationRequestBasedOnCookieRepository()))
                                                .userInfoEndpoint(userInfo -> userInfo.userService(customService))
                                                .successHandler(oAuth2SuccessHandler()))
                                .exceptionHandling(exceptions -> exceptions
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                new AntPathRequestMatcher("/api/**")))
                                .build();
        }

        @Bean
        public OAuth2SuccessHandler oAuth2SuccessHandler() {
                return new OAuth2SuccessHandler(tokenProvider,
                                refreshTokenRepository,
                                oAuth2AuthorizationRequestBasedOnCookieRepository(),
                                userService);
        }

        @Bean
        public TokenAuthenticationFilter tokenAuthenticationFilter() {
                return new TokenAuthenticationFilter(tokenProvider);
        }

        @Bean
        public OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository() {
                return new OAuth2AuthorizationRequestBasedOnCookieRepository();
        }
}
