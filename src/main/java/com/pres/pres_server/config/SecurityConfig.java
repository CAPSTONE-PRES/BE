package com.pres.pres_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
    // @Autowired
    // private OAuth2UserCustomService oAuth2UserCustomService;

    // @Bean
    // public SecurityFilterChain securityFilterChain(HttpSecurity http) throws
    // Exception {
    // http
    // .csrf(csrf -> csrf.disable())
    // .authorizeHttpRequests(auth -> auth
    // .requestMatchers("/", "/login/**", "/oauth2/**").permitAll()
    // .anyRequest().authenticated())
    // .oauth2Login(oauth2 -> oauth2
    // .userInfoEndpoint(userInfo -> userInfo
    // .userService(oAuth2UserCustomService)));
    // return http.build();
    // }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
