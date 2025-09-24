package com.pres.pres_server.service.user;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.User.UserUpdateDto;
import com.pres.pres_server.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일로 사용자를 찾을 수 없습니다: " + email));
    }

    // 사용자 정보 변경
    @Transactional
    public User updateUser(Long id, UserUpdateDto dto) {
        User user = getUser(id);
        if (dto.getUsername() != null && dto.getUsername().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름이 비어 있습니다.");
        }
        if (dto.getEmail() != null && dto.getEmail().isEmpty()) {
            throw new IllegalArgumentException("이메일이 비어 있습니다.");
        }
        if (dto.getPassword() != null && dto.getPassword().length() < 8) {
            throw new IllegalArgumentException("비밀번호가 너무 짧습니다.");
        }
        if (dto.getUsername() != null) {
            user.setUsername(dto.getUsername());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userRepository.save(user);
        return user;
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
}
