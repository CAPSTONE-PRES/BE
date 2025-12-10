package com.pres.pres_server.security.jwt;

import com.pres.pres_server.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import java.util.Collections;
import java.util.Set;
import java.time.Duration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.ExpiredJwtException;
import com.pres.pres_server.domain.User;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class TokenProvider {

    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;

    public String generateToken(User user, Duration expiredAt) {
        Date now = new Date();
        return makeToken(new Date(now.getTime() + expiredAt.toMillis()), user);
    }

    // 토큰 생성 메서드
    // HS256 방식 사용
    // 클레임에 유저 ID 지정
    private String makeToken(Date expiration, User user) {
        Date now = new Date();

        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE) // 헤더 hyp : JWT
                .setIssuer(jwtProperties.getIssuer())
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .claim("id", user.getId())
                .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecretKey())
                .compact();
    }

    // 토큰 유효성 검사
    public boolean validToken(String token) {
        try {
            Jwts.parser().setSigningKey(jwtProperties.getSecretKey()).parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException eje) {
            // 만료된 토큰은 흔한 상황이므로 info로 기록
            log.info("Expired JWT token: {}", eje.getMessage());
        } catch (Exception e) {
            // 서명 오류 등 예외는 경고 수준
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    // 토큰 기반으로 인증 정보를 가져옴
    // 스프링 시큐리티에서 제공하는 객체인 User 클래스 import
    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        Long userId = claims.get("id", Long.class);

        User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException(
                "User not found" + userId));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    // 토큰 기반으로 유저 id를 가져오는 메서드
    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return claims.get("id", Long.class);
    }

    // 토큰에서 클레임을 추출하는 메서드
    // 클레임 : 토큰에 담기는 정보의 단위
    private Claims getClaims(String token) {
        return Jwts.parser() // 클레임 조회
                .setSigningKey(jwtProperties.getSecretKey())
                .parseClaimsJws(token)
                .getBody();
    }

    public String resolveRefreshToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 헤더에 없으면 쿠키에서 찾기
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("refresh_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}