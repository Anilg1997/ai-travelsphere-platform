package com.travelsphere.auth.service;

import com.travelsphere.auth.config.JwtUtil;
import com.travelsphere.auth.dto.AuthResponse;
import com.travelsphere.auth.dto.ChangeEmailRequest;
import com.travelsphere.auth.dto.ChangePasswordRequest;
import com.travelsphere.auth.dto.LoginRequest;
import com.travelsphere.auth.dto.RegisterRequest;
import com.travelsphere.auth.model.LoyaltyTier;
import com.travelsphere.auth.model.Role;
import com.travelsphere.auth.model.User;
import com.travelsphere.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private AuthServiceImpl authService;

    private static final String TEST_EMAIL = "test@email.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NAME = "Test User";
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_TOKEN = "test.jwt.token";
    private static final String TEST_REFRESH_TOKEN = "test.refresh.token";
    private static final String TEST_TOKEN_ID = "token-id-123";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void registerSuccess() {
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL).password(TEST_PASSWORD).fullName(TEST_NAME).build();

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(TEST_USER_ID);
            return u;
        });
        when(jwtUtil.generateAccessToken(any(), any(), any(), any())).thenReturn(TEST_TOKEN);
        when(jwtUtil.generateRefreshToken(any())).thenReturn(TEST_REFRESH_TOKEN);
        when(jwtUtil.getTokenId(TEST_REFRESH_TOKEN)).thenReturn(TEST_TOKEN_ID);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(TEST_EMAIL, response.getEmail());
        assertEquals(TEST_NAME, response.getFullName());
        assertEquals(TEST_TOKEN, response.getAccessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getRefreshToken());
        assertEquals("SILVER", response.getTier());
        assertTrue(response.getRoles().contains("ROLE_USER"));

        verify(userRepository).save(any(User.class));
        verify(hashOperations).put(anyString(), eq(TEST_TOKEN_ID), eq(TEST_REFRESH_TOKEN));
        verify(kafkaTemplate).send(eq("ts.users.registered"), anyString(), any());
    }

    @Test
    void registerDuplicateEmailThrows() {
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL).password(TEST_PASSWORD).fullName(TEST_NAME).build();
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    void loginSuccess() {
        LoginRequest request = LoginRequest.builder().email(TEST_EMAIL).password(TEST_PASSWORD).build();
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded")
                .fullName(TEST_NAME).isActive(true).loyaltyTier(LoyaltyTier.SILVER)
                .roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, "encoded")).thenReturn(true);
        when(jwtUtil.generateAccessToken(any(), any(), any(), any())).thenReturn(TEST_TOKEN);
        when(jwtUtil.generateRefreshToken(any())).thenReturn(TEST_REFRESH_TOKEN);
        when(jwtUtil.getTokenId(TEST_REFRESH_TOKEN)).thenReturn(TEST_TOKEN_ID);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals(TEST_EMAIL, response.getEmail());
        assertEquals(TEST_TOKEN, response.getAccessToken());
    }

    @Test
    void loginInvalidEmailThrows() {
        LoginRequest request = LoginRequest.builder().email("wrong@email.com").password(TEST_PASSWORD).build();
        when(userRepository.findByEmail("wrong@email.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
    }

    @Test
    void loginInvalidPasswordThrows() {
        LoginRequest request = LoginRequest.builder().email(TEST_EMAIL).password("wrong").build();
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded")
                .isActive(true).roles(Set.of(Role.ROLE_USER)).loyaltyTier(LoyaltyTier.SILVER).build();

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
    }

    @Test
    void loginDeactivatedAccountThrows() {
        LoginRequest request = LoginRequest.builder().email(TEST_EMAIL).password(TEST_PASSWORD).build();
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded")
                .isActive(false).roles(Set.of(Role.ROLE_USER)).loyaltyTier(LoyaltyTier.SILVER).build();

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, "encoded")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> authService.login(request));
    }

    @Test
    void logoutAddsBlacklistAndDeletesRefresh() {
        Jws<Claims> mockClaims = mock(Jws.class);
        Claims mockPayload = mock(Claims.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);

        when(jwtUtil.validateToken(TEST_TOKEN)).thenReturn(mockClaims);
        when(mockClaims.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getId()).thenReturn(TEST_TOKEN_ID);
        when(mockPayload.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60000));
        when(mockPayload.getSubject()).thenReturn(TEST_USER_ID.toString());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        authService.logout(TEST_TOKEN);

        verify(valueOps).set(eq("blacklist:" + TEST_TOKEN_ID), eq("1"), anyLong(), any());
        verify(redisTemplate).delete("refresh:" + TEST_USER_ID);
    }

    // ── Change Password Tests ─────────────────────────────

    @Test
    void changePasswordSuccess() {
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded-old")
                .fullName(TEST_NAME).isActive(true).loyaltyTier(LoyaltyTier.SILVER)
                .roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass123", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newPass456")).thenReturn("encoded-new");
        when(userRepository.save(any(User.class))).thenReturn(user);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass123")
                .newPassword("newPass456")
                .build();

        authService.changePassword(TEST_USER_ID, request);

        verify(passwordEncoder).encode("newPass456");
        verify(userRepository).save(any(User.class));
        verify(redisTemplate).delete("refresh:" + TEST_USER_ID);
    }

    @Test
    void changePasswordUserNotFoundThrows() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass123")
                .newPassword("newPass456")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(TEST_USER_ID, request));
    }

    @Test
    void changePasswordIncorrectCurrentPasswordThrows() {
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded-old")
                .fullName(TEST_NAME).isActive(true).loyaltyTier(LoyaltyTier.SILVER)
                .roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encoded-old")).thenReturn(false);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrongPassword")
                .newPassword("newPass456")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(TEST_USER_ID, request));
        assertEquals("Current password is incorrect", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    // ── Change Email Tests ──────────────────────────────

    @Test
    void changeEmailSuccess() {
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded")
                .fullName(TEST_NAME).isActive(true).isEmailVerified(true)
                .loyaltyTier(LoyaltyTier.SILVER).roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(userRepository.existsByEmail("new@email.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        ChangeEmailRequest request = ChangeEmailRequest.builder()
                .newEmail("new@email.com")
                .currentPassword("password123")
                .build();

        authService.changeEmail(TEST_USER_ID, request);

        verify(userRepository).save(argThat(u -> {
            User saved = (User) u;
            return "new@email.com".equals(saved.getEmail()) && !saved.isEmailVerified();
        }));
        verify(redisTemplate).delete("refresh:" + TEST_USER_ID);
        verify(kafkaTemplate).send(eq("ts.users.email-changed"), eq(TEST_USER_ID.toString()), eq(TEST_USER_ID));
    }

    @Test
    void changeEmailUserNotFoundThrows() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        ChangeEmailRequest request = ChangeEmailRequest.builder()
                .newEmail("new@email.com")
                .currentPassword("password123")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> authService.changeEmail(TEST_USER_ID, request));
    }

    @Test
    void changeEmailIncorrectPasswordThrows() {
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded")
                .isActive(true).loyaltyTier(LoyaltyTier.SILVER)
                .roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encoded")).thenReturn(false);

        ChangeEmailRequest request = ChangeEmailRequest.builder()
                .newEmail("new@email.com")
                .currentPassword("wrongPassword")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changeEmail(TEST_USER_ID, request));
        assertEquals("Current password is incorrect", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeEmailAlreadyTakenThrows() {
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).passwordHash("encoded")
                .isActive(true).loyaltyTier(LoyaltyTier.SILVER)
                .roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(userRepository.existsByEmail("taken@email.com")).thenReturn(true);

        ChangeEmailRequest request = ChangeEmailRequest.builder()
                .newEmail("taken@email.com")
                .currentPassword("password123")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changeEmail(TEST_USER_ID, request));
        assertEquals("Email address is already in use", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    // ── Delete Account Tests ──────────────────────────────

    @Test
    void deleteAccountSuccess() {
        User user = User.builder()
                .id(TEST_USER_ID).email(TEST_EMAIL).isActive(true)
                .loyaltyTier(LoyaltyTier.SILVER).roles(Set.of(Role.ROLE_USER)).build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        authService.deleteAccount(TEST_USER_ID);

        verify(userRepository).save(argThat(u -> !((User) u).isActive()));
        verify(redisTemplate).delete("refresh:" + TEST_USER_ID);
        verify(kafkaTemplate).send(eq("ts.users.deleted"), eq(TEST_USER_ID.toString()), eq(TEST_USER_ID));
    }

    @Test
    void deleteAccountUserNotFoundThrows() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.deleteAccount(TEST_USER_ID));
    }
}
