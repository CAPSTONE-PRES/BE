// package com.pres.pres_server.config;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import
// org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.web.SecurityFilterChain;
// import
// org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import
// org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
// import
// org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
// import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.authentication.ProviderManager;
// import
// org.springframework.security.authentication.dao.DaoAuthenticationProvider;
// import
// org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import com.pres.pres_server.service.user.UserService;

// import lombok.RequiredArgsConstructor;

// @Configuration
// @EnableWebSecurity
// @RequiredArgsConstructor
// public class WebSecurityConfig {

// private final UserService userService;

// // 스프링 시큐리티 기능 비활성화
// @Bean
// public WebSecurityCustomizer configure() {
// return (web) -> web.ignoring().requestMatchers("/h2-console/**")
// .requestMatchers("/static/**");
// }

// // 특정 http 요청에 대한 웹 기반 보안 구성
// @Bean
// public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
// return http.authorizeHttpRequests((auth) -> auth
// .requestMatchers("/login", "/signup", "/user").permitAll() // /login,
// /signup, /user 경로는 인증 없이 접근 허용
// .anyRequest().authenticated())
// .formLogin(form -> form
// .loginPage("/login") // 커스텀 로그인 페이지 경로
// .defaultSuccessUrl("/home", true) // 로그인 성공 시 이동할 경로
// .failureUrl("/login?error=true") // 로그인 실패 시 이동할 경로
// .permitAll())
// .logout(logout -> logout
// .logoutUrl("/logout") // 로그아웃 처리 URL
// .logoutSuccessUrl("/login") // 로그아웃 성공 시 이동할 경로
// .invalidateHttpSession(true) // 세션 무효화
// .deleteCookies("JSESSIONID") // 쿠키 삭제
// .permitAll())
// .csrf(AbstractHttpConfigurer::disable) // CSRF 보호 비활성화 (개발 중에만 사용, 실제 서비스에서는
// 활성화 필요)
// .build();
// }

// // 인증 관리자 관련 설정
// @Bean
// public AuthenticationManager authenticationManager(BCryptPasswordEncoder
// bCryptPasswordEncoder, HttpSecurity http,
// UserService userService) throws Exception {
// DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
// authProvider.setUserDetailsService(userService); // 사용자 정보 서비스 설정
// authProvider.setPasswordEncoder(bCryptPasswordEncoder); // 비밀번호 인코더 설정
// return new ProviderManager(authProvider);
// }

// // BCryptPasswordEncoder 빈 등록
// @Bean
// public BCryptPasswordEncoder bCryptPasswordEncoder() {
// return new BCryptPasswordEncoder();
// }

// }