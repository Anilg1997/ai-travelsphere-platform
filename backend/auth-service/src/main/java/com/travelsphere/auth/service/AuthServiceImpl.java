package com.travelsphere.auth.service;

import com.travelsphere.auth.config.JwtUtil;
import com.travelsphere.auth.dto.AuthResponse;
import com.travelsphere.auth.dto.ChangePasswordRequest;
import com.travelsphere.auth.dto.ChangeEmailRequest;
import com.travelsphere.auth.dto.LoginRequest;
import com.travelsphere.auth.dto.RegisterRequest;
import com.travelsphere.auth.model.Role;
import com.travelsphere.auth.model.User;
import com.travelsphere.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isEmailVerified(false)
                .isActive(true)
                .roles(Set.of(Role.ROLE_USER))
                .build();

        user = userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(),
                Set.of("ROLE_USER"), "SILVER");
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Store refresh token in Redis
        String refreshTokenId = jwtUtil.getTokenId(refreshToken);
        redisTemplate.opsForHash().put(
                "refresh:" + user.getId(),
                refreshTokenId,
                refreshToken);
        redisTemplate.expire("refresh:" + user.getId(), 7, TimeUnit.DAYS);

        // Publish user registered event
        kafkaTemplate.send("ts.users.registered", user.getId().toString(), user);

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(Set.of("ROLE_USER"))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(900000)
                .tier("SILVER")
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        Set<String> roleStrings = Set.of(user.getRoles().stream()
                .map(Enum::name)
                .toArray(String[]::new));

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), roleStrings, user.getLoyaltyTier().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        String refreshTokenId = jwtUtil.getTokenId(refreshToken);
        redisTemplate.opsForHash().put(
                "refresh:" + user.getId(),
                refreshTokenId,
                refreshToken);
        redisTemplate.expire("refresh:" + user.getId(), 7, TimeUnit.DAYS);

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roleStrings)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(900000)
                .tier(user.getLoyaltyTier().name())
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        var claims = jwtUtil.validateToken(refreshToken);
        String tokenId = claims.getPayload().getId();
        String userIdStr = claims.getPayload().getSubject();

        // Verify token exists in Redis
        String storedToken = (String) redisTemplate.opsForHash()
                .get("refresh:" + userIdStr, tokenId);
        if (storedToken == null) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        User user = userRepository.findById(java.util.UUID.fromString(userIdStr))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Set<String> roleStrings = Set.of(user.getRoles().stream()
                .map(Enum::name)
                .toArray(String[]::new));

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), roleStrings, user.getLoyaltyTier().name());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roleStrings)
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(900000)
                .tier(user.getLoyaltyTier().name())
                .build();
    }

    @Override
    public void logout(String accessToken) {
        var claims = jwtUtil.validateToken(accessToken);
        String tokenId = claims.getPayload().getId();
        long expiration = claims.getPayload().getExpiration().getTime() - System.currentTimeMillis();

        if (expiration > 0) {
            redisTemplate.opsForValue().set(
                    "blacklist:" + tokenId, "1", expiration, TimeUnit.MILLISECONDS);
        }

        // Remove refresh tokens
        String userId = claims.getPayload().getSubject();
        redisTemplate.delete("refresh:" + userId);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all existing refresh tokens for this user
        redisTemplate.delete("refresh:" + userId);
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Soft-delete: deactivate instead of hard delete
        user.setActive(false);
        userRepository.save(user);

        // Invalidate all refresh tokens
        redisTemplate.delete("refresh:" + userId);

        // Publish account deleted event for other services to clean up
        kafkaTemplate.send("ts.users.deleted", userId.toString(), userId);
    }

    @Override
    @Transactional
    public void changeEmail(UUID userId, ChangeEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Check if new email is already taken
        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new IllegalArgumentException("Email address is already in use");
        }

        user.setEmail(request.getNewEmail());
        user.setEmailVerified(false);
        userRepository.save(user);

        // Invalidate all existing refresh tokens (new email will be in new tokens)
        redisTemplate.delete("refresh:" + userId);

        // Publish email changed event
        kafkaTemplate.send("ts.users.email-changed", userId.toString(), userId);
    }
}
