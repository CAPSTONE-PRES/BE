package com.pres.pres_server.service.user;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.User.UserResponseDto;
import com.pres.pres_server.dto.User.UserUpdateDto;
import com.pres.pres_server.dto.User.UserValidationResponseDTO;
import com.pres.pres_server.repository.UserRepository;

import com.pres.pres_server.service.DefaultProfileImageService;
import com.pres.pres_server.service.S3Service;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserService implements UserDetailsService {
    @Value("${app.cdn.base-url}")
    private String cdnBaseUrl;

    private final DefaultProfileImageService defaultProfileImageService;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;

    @Override
    public User loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일로 사용자를 찾을 수 없습니다: " + email));
    }

    // 사용자 정보 변경
    @Transactional
    public User updateUser(Long id, UserUpdateDto dto) {
        log.info("updateUser called - id={}, dto={}", id, dto);
        User user = getUser(id);

        // 입력값 간단 검증 (null은 패치에서 '미변경' 의미이므로 길이 검증은 hasText일 때만)
        if (dto.getUsername() != null && dto.getUsername().isEmpty())
            throw new IllegalArgumentException("사용자 이름이 비어 있습니다.");
        if (dto.getEmail() != null && dto.getEmail().isEmpty())
            throw new IllegalArgumentException("이메일이 비어 있습니다.");
        if (dto.getPassword() != null && dto.getPassword().length() < 8)
            throw new IllegalArgumentException("비밀번호가 너무 짧습니다. 8자 이상 작성해주세요.");

        // 실제 값이 달라졌을 때만 변경
        if (dto.getUsername() != null && !Objects.equals(dto.getUsername(), user.getUsername())) {
            user.setUsername(dto.getUsername().trim());
        }
        if (dto.getEmail() != null && !Objects.equals(dto.getEmail(), user.getEmail())) {
            user.setEmail(dto.getEmail().trim());
            // 이메일 변경 시 검증 플래그 리셋 고려
            user.setEmailVerified(false);
        }
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            // 같은 비번 재설정 방지: 해시 비교
            if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
        }
        User saved = userRepository.save(user);
        log.info("User updated - id={}, username(before->after)={}->{}", id, user.getUsername(), saved.getUsername());
        return saved;
    }

    /**
     * 프로필 이미지 업데이트 (파일 업로드)
     */
    @Transactional
    public User updateProfileImage(Long userId, MultipartFile file) {
        User user = getUser(userId);
        String oldKey = user.getProfileImageKey();

        String newKey = s3Service.uploadAndReturnKey(file);

        if (oldKey != null && !defaultProfileImageService.isDefaultKey(oldKey)) {
            s3Service.delete(oldKey);
        }
        user.setProfileImageKey(newKey);
        return userRepository.save(user);
    }

    /**
     * 프로필 이미지 삭제 (기본 이미지로 복원)
     */
    @Transactional
    public User deleteProfileImage(Long userId) {
        User user = getUser(userId);
        String oldKey = user.getProfileImageKey();

        if (oldKey != null && !defaultProfileImageService.isDefaultKey(oldKey)) {
            s3Service.delete(oldKey);
        }
        user.setProfileImageKey(defaultProfileImageService.pickDefaultKey(user.getEmail()));
        return userRepository.save(user);
    }

    public String resolveProfileKey(User user) {
        String key = user.getProfileImageKey();
        return (key == null || key.isBlank())
                ? defaultProfileImageService.pickDefaultKey(user.getEmail())
                : key;
    }

    public String resolveProfileUrl(User user) {
        return cdnBaseUrl + "/" + resolveProfileKey(user);
    }

    // 컨트롤러가 이걸 쓰게 함
    public UserResponseDto toDto(User user) {
        return UserResponseDto.of(user, resolveProfileUrl(user));
    }

    // 비밀번호 변경
    @Transactional
    public void updatePassword(String email, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("비밀번호가 너무 짧습니다.");
        }
        User user = findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("비밀번호 변경 대상 사용자를 찾을 수 없습니다: " + email);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // 사용자 삭제
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("삭제할 사용자를 찾을 수 없습니다. id=" + id);
        }
        userRepository.deleteById(id);
    }

    // 사용자 조회
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("id로 사용자를 찾을 수 없습니다: " + id));
    }

    // 모든 사용자 목록 조회 (관리자용)
    public List<User> listUsers() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            throw new IllegalArgumentException("등록된 사용자가 없습니다.");
        }
        return users;
    }

    // id로 사용자 찾기
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("id로 사용자를 찾을 수 없습니다: " + id));
    }

    // email로 사용자 찾기
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일로 사용자를 찾을 수 없습니다: " + email));
    }

    // 안전한 Optional 반환 메서드 (존재 여부 확인용)
    public Optional<User> findOptionalByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // 이메일 유효성 검사
    public UserValidationResponseDTO validateUserEmail(String memberEmail) {
        return userRepository.findByEmail(memberEmail)
                .map(user -> UserValidationResponseDTO.builder()
                        .message("유효한 이메일 입니다")
                        .name(user.getUsername())
                        .userId(user.getId())
                        .email(user.getEmail())
                        .profileUrl(resolveProfileUrl(user))
                        .build())
                .orElseGet(() -> UserValidationResponseDTO.builder()
                        .message("유효하지 않은 이메일 입니다")
                        .build());
    }
}
