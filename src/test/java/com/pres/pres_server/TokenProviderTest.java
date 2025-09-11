// package com.pres.pres_server;

// import java.time.Duration;
// import java.util.Date;

// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.security.core.Authentication;

// import com.pres.pres_server.config.jwt.TokenProvider;
// import com.pres.pres_server.repository.UserRepository;
// import com.pres.pres_server.domain.User;
// import com.pres.pres_server.config.jwt.JwtProperties;
// import static org.assertj.core.api.Assertions.assertThat;
// import io.jsonwebtoken.Jwts;
// import io.jsonwebtoken.Header;
// import org.springframework.security.core.userdetails.UserDetails;
// import java.util.Map;

// @SpringBootTest
// public class TokenProviderTest {
//     @Autowired
//     private TokenProvider tokenProvider;
//     @Autowired
//     private JwtProperties jwtProperties;
//     @Autowired
//     private UserRepository userRepository;

//     // generateToken() 검증 테스트
//     @DisplayName("generateToken(): 토큰 생성 테스트")
//     @Test
//     void generateToken() {
//         // given
//         User testUser = userRepository.save(User.builder()
//                 .email("test@example.com")
//                 .password("password")
//                 .build());

//         // when
//         String token = tokenProvider.generateToken(testUser, Duration.ofDays(14));

//         // then
//         Long userId = Jwts.parser().setSigningKey(jwtProperties.getSecretKey())
//                 .parseClaimsJws(token)
//                 .getBody()
//                 .get("userId", Long.class);

//         assertThat(userId).isEqualTo(testUser.getId());
//     }

//     // validToken() 검증 테스트
//     @DisplayName("validToken(): 만료된 토큰일 시 유효성 검증 실패")
//     @Test
//     void validToken_Expired() {
//         // given
//         String token = JwtFactory.builder().expiration(new Date(new Date().getTime() - Duration.ofDays(7).toMillis()))
//                 .build().createToken(jwtProperties);
//         // when
//         boolean result = tokenProvider.validToken(token);

//         // then
//         assertThat(result).isFalse();
//     }

//     // 유효한 토큰일 때 유효성 검증에 성공
//     @DisplayName("validToken(): 유효한 토큰일 시 유효성 검증 성공")
//     @Test
//     void validToken_Success() {
//         // given
//         String token = JwtFactory.builder().expiration(new Date(new Date().getTime() - Duration.ofDays(7).toMillis()))
//                 .build().createToken(jwtProperties);
//         // when
//         boolean result = tokenProvider.validToken(token);

//         // then
//         assertThat(result).isTrue();
//     }

//     // getAuthentication() 검증 테스트
//     @DisplayName("getAuthentication(): 토큰 기반으로 인증 정보 조회")
//     @Test
//     void getAuthentication() {
//         // given
//         String email = "test@example.com";
//         String token = JwtFactory.builder().subject(email).build().createToken(jwtProperties);
//         // when
//         Authentication auth = tokenProvider.getAuthentication(token);
//         // then
//         assertThat(((UserDetails) auth.getPrincipal()).getUsername()).isEqualTo(email);
//     }

//     // getUserId() 검증 테스트
//     @DisplayName("getUserId(): 토큰 기반으로 유저 ID 조회")
//     @Test
//     void getUserId() {
//         // given
//         Long userId = 123L;
//         String token = JwtFactory.builder().claims(Map.of("userId", userId)).build().createToken(jwtProperties);
//         // when
//         Long userIdByToken = tokenProvider.getUserId(token);
//         // then
//         assertThat(userIdByToken).isEqualTo(userId);
//     }
// }
