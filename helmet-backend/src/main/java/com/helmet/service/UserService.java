package com.helmet.service;

import com.helmet.model.User;
import com.helmet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User createUser(String username, String password, String email, String fullName) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Tên đăng nhập và mật khẩu không được để trống.");
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }

        Instant now = Instant.now();
        return userRepository.save(User.builder()
                .username(normalizedUsername)
                .passwordHash(passwordEncoder.encode(password))
                .email(email == null ? "" : email.trim())
                .fullName(fullName == null ? "" : fullName.trim())
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
