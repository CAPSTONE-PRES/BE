// package com.pres.pres_server;

// import java.util.Date;
// import java.util.Map;

// import io.jsonwebtoken.Header;
// import io.jsonwebtoken.Jwts;
// import lombok.Builder;
// import lombok.Getter;
// import io.jsonwebtoken.SignatureAlgorithm;
// import static java.util.Collections.emptyMap;
// import com.pres.pres_server.config.jwt.JwtProperties;

// // jwt 토큰 서비스 테스트에 사용할 모킹용 객체

// @Getter
// public class JwtFactory {
//     private String subject = "test@email.com";
//     private Date issuedAt = new Date();
//     private Date expiration = new Date(issuedAt.getTime() + 1000 * 60 * 60); // 1 hour later
//     private Map<String, Object> claims = emptyMap();

//     // 빌더 패턴으로 설정이 필요한 데이터만 선택
//     @Builder
//     public JwtFactory(String subject, Date issuedAt, Date expiration, Map<String, Object> claims) {
//         this.subject = subject != null ? subject : this.subject;
//         this.issuedAt = issuedAt != null ? issuedAt : this.issuedAt;
//         this.expiration = expiration != null ? expiration : this.expiration;
//         this.claims = claims != null ? claims : this.claims;
//     }

//     public static JwtFactory withDefaultValues() {
//         return JwtFactory.builder().build();
//     }

//     // jjwt 라이브러리 사용해 jwt 토큰 생성
//     public String createToken(JwtProperties jwtProperties) {
//         return Jwts.builder()
//                 .setSubject(subject)
//                 .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
//                 .setIssuedAt(issuedAt)
//                 .setExpiration(expiration)
//                 .addClaims(claims)
//                 .setIssuer(jwtProperties.getIssuer())
//                 .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecretKey())
//                 .compact();
//     }

// }
