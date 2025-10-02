package com.pres.pres_server;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.User.UserUpdateDto;
import com.pres.pres_server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.pres.pres_server.service.user.UserService;

import static org.assertj.core.api.Assertions.assertThat;
/*
 * @AutoConfigureMockMvc(addFilters = false) // 시큐리티 필터 비적용
 * 
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
 * class UserServiceTest {
 * 
 * @MockitoBean
 * ClientRegistrationRepository clientRegistrationRepository;
 * 
 * @MockitoBean
 * OAuth2AuthorizedClientRepository authorizedClientRepository;
 * 
 * @Autowired
 * private UserService userService;
 * 
 * @Autowired
 * private UserRepository userRepository;
 * 
 * @BeforeEach
 * void setUp() {
 * userRepository.deleteAll();
 * }
 * 
 * @Test
 * 
 * @DisplayName("사용자 생성 및 조회")
 * void createAndGetUser() {
 * User user = userRepository.save(User.builder()
 * .email("test@email.com")
 * .password("pw")
 * .username("tester")
 * .build());
 * 
 * User found = userService.getUser(user.getId());
 * assertThat(found.getEmail()).isEqualTo("test@email.com");
 * assertThat(found.getUsername()).isEqualTo("tester");
 * }
 * 
 * @Test
 * 
 * @DisplayName("사용자 정보 수정")
 * 
 * @Transactional
 * void updateUser() {
 * User user = userRepository.save(User.builder()
 * .email("test@email.com")
 * .password("pw")
 * .username("tester")
 * .build());
 * 
 * UserUpdateDto dto = new UserUpdateDto();
 * dto.setUsername("updated");
 * dto.setEmail("new@email.com");
 * dto.setPassword("newpw");
 * 
 * User updated = userService.updateUser(user.getId(), dto);
 * 
 * assertThat(updated.getUsername()).isEqualTo("updated");
 * assertThat(updated.getEmail()).isEqualTo("new@email.com");
 * assertThat(userService.getUser(user.getId()).getUsername()).isEqualTo(
 * "updated");
 * }
 * 
 * @Test
 * 
 * @DisplayName("사용자 삭제")
 * 
 * @Transactional
 * void deleteUser() {
 * User user = userRepository.save(User.builder()
 * .email("test@email.com")
 * .password("pw")
 * .username("tester")
 * .build());
 * 
 * userService.deleteUser(user.getId());
 * assertThat(userRepository.findById(user.getId())).isEmpty();
 * }
 * 
 * @Test
 * 
 * @DisplayName("전체 사용자 목록 조회")
 * void listUsers() {
 * userRepository.save(User.builder().email("a@email.com").password("pw").
 * username("a").build());
 * userRepository.save(User.builder().email("b@email.com").password("pw").
 * username("b").build());
 * 
 * assertThat(userService.listUsers()).hasSize(2);
 * }
 * }
 */