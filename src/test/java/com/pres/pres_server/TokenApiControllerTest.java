// package com.pres.pres_server;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.pres.pres_server.dto.TokenDto;
// import com.pres.pres_server.dto.TokenDto.CreateAccessTokenRequest;
// import com.pres.pres_server.repository.RefreshTokenRepository;
// import com.pres.pres_server.repository.UserRepository;
// import com.pres.pres_server.config.jwt.JwtProperties;
// import com.pres.pres_server.service.token.TokenService;
// import com.pres.pres_server.controller.TokenApiController;
// import com.pres.pres_server.domain.RefreshToken;
// import com.pres.pres_server.domain.User;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Lettuce.Cluster.Refresh;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.ResultActions;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import org.springframework.web.context.WebApplicationContext;

// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// import java.util.Map;

// import javax.print.attribute.standard.Media;

// @SpringBootTest
// @AutoConfigureMockMvc
// class TokenApiControllerTest {

//     @Autowired
//     private MockMvc mockMvc;

//     @Autowired
//     private ObjectMapper objectMapper;

//     @Autowired
//     private WebApplicationContext context;

//     @Autowired
//     private JwtProperties jwtProperties;

//     @Autowired
//     private TokenService tokenService;

//     @Autowired
//     private UserRepository userRepository;

//     @Autowired
//     private RefreshTokenRepository refreshTokenRepository;

//     @BeforeEach
//     void setUp() {
//         this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
//         userRepository.deleteAll();
//     }

//     @DisplayName("createAccessToken: 새로운 토큰 발급")
//     @Test
//     void createAccessToken() throws Exception {
//         // given
//         final String url = "/api/token";

//         User testUser = userRepository.save(User.builder().email("user@gmail.com")
//                 .password("test")
//                 .build());

//         String refreshToken = JwtFactory.builder().claims(Map.of("userId", testUser.getId()))
//                 .build().createToken(jwtProperties);

//         refreshTokenRepository.save(new RefreshToken(testUser.getId(), refreshToken));

//         CreateAccessTokenRequest request = new CreateAccessTokenRequest();
//         request.setRefreshToken(refreshToken);
//         final String requestBody = objectMapper.writeValueAsString(request);

//         // when
//         ResultActions result = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON_VALUE)
//                 .content(requestBody));

//         // then
//         result.andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").isNotEmpty());
//     }
// }
