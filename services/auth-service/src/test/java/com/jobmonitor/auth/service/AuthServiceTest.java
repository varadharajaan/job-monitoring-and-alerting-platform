package com.jobmonitor.auth.service;

import com.jobmonitor.auth.dto.*;
import com.jobmonitor.auth.entity.ApiKey;
import com.jobmonitor.auth.entity.User;
import com.jobmonitor.auth.repository.ApiKeyRepository;
import com.jobmonitor.auth.repository.UserRepository;
import com.jobmonitor.platform.common.exception.BusinessException;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    private static final String TENANT_ID = "tenant-auth-001";
    private static final UUID USER_ID = UUID.randomUUID();

    private User createUser() {
        var user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$10$hashedpassword");
        user.setFullName("Test User");
        user.setRole(User.Role.OPERATOR);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setVersion(0L);
        return user;
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, apiKeyRepository, passwordEncoder, jwtTokenProvider);
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should return auth response on valid credentials")
        void shouldLoginSuccessfully() {
            var request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");
            var user = createUser();

            given(userRepository.findByUsernameAndEnabledTrue("testuser"))
                    .willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123", user.getPasswordHash()))
                    .willReturn(true);
            given(jwtTokenProvider.generateToken(anyString(), eq("testuser"), eq(TENANT_ID), anyList()))
                    .willReturn("access-token-123");
            given(jwtTokenProvider.generateRefreshToken(anyString()))
                    .willReturn("refresh-token-456");

            var result = authService.login(request);

            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("access-token-123");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token-456");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getUsername()).isEqualTo("testuser");
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("should throw BusinessException on invalid credentials")
        void shouldThrowOnInvalidCredentials() {
            var request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpassword");

            given(userRepository.findByUsernameAndEnabledTrue("testuser"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid username or password");
        }

        @Test
        @DisplayName("should throw when password does not match")
        void shouldThrowOnPasswordMismatch() {
            var request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpassword");
            var user = createUser();

            given(userRepository.findByUsernameAndEnabledTrue("testuser"))
                    .willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongpassword", user.getPasswordHash()))
                    .willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register user successfully")
        void shouldRegisterUser() {
            var request = new RegisterRequest();
            request.setTenantId(TENANT_ID);
            request.setUsername("newuser");
            request.setEmail("new@example.com");
            request.setPassword("password123");
            request.setFullName("New User");
            request.setRole("OPERATOR");

            given(userRepository.existsByUsername("newuser")).willReturn(false);
            given(userRepository.existsByEmail("new@example.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("$2a$10$encoded");
            given(userRepository.save(any(User.class))).willAnswer(inv -> {
                var user = inv.getArgument(0, User.class);
                user.setId(USER_ID);
                user.setCreatedAt(Instant.now());
                return user;
            });

            var result = authService.register(request);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("newuser");
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException on duplicate username")
        void shouldThrowOnDuplicateUsername() {
            var request = new RegisterRequest();
            request.setTenantId(TENANT_ID);
            request.setUsername("existing");
            request.setEmail("new@example.com");
            request.setPassword("password123");

            given(userRepository.existsByUsername("existing")).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should throw DuplicateResourceException on duplicate email")
        void shouldThrowOnDuplicateEmail() {
            var request = new RegisterRequest();
            request.setTenantId(TENANT_ID);
            request.setUsername("newuser");
            request.setEmail("existing@example.com");
            request.setPassword("password123");

            given(userRepository.existsByUsername("newuser")).willReturn(false);
            given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("should return new tokens on valid refresh token")
        void shouldRefreshTokenSuccessfully() {
            var user = createUser();
            given(jwtTokenProvider.extractUserId("valid-refresh-token"))
                    .willReturn(Optional.of(USER_ID.toString()));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(jwtTokenProvider.generateToken(anyString(), eq("testuser"), eq(TENANT_ID), anyList()))
                    .willReturn("new-access-token");
            given(jwtTokenProvider.generateRefreshToken(anyString()))
                    .willReturn("new-refresh-token");

            var result = authService.refreshToken("valid-refresh-token");

            assertThat(result.getAccessToken()).isEqualTo("new-access-token");
            assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("should throw BusinessException on invalid refresh token")
        void shouldThrowOnInvalidRefreshToken() {
            given(jwtTokenProvider.extractUserId("invalid-token"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("invalid-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid or expired refresh token");
        }
    }

    @Nested
    @DisplayName("createApiKey()")
    class CreateApiKeyTests {

        @Test
        @DisplayName("should create API key and return raw key")
        void shouldCreateApiKey() {
            var user = createUser();
            var request = new CreateApiKeyRequest();
            request.setName("my-key");
            request.setScopes(List.of("READ", "WRITE"));

            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(apiKeyRepository.existsByTenantIdAndName(TENANT_ID, "my-key")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("$2a$10$keyhash");
            given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(inv -> {
                var key = inv.getArgument(0, ApiKey.class);
                key.setId(UUID.randomUUID());
                key.setCreatedAt(Instant.now());
                return key;
            });

            var result = authService.createApiKey(USER_ID.toString(), TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("my-key");
            assertThat(result.getRawKey()).startsWith("jm_");
            assertThat(result.getScopes()).containsExactly("READ", "WRITE");
        }

        @Test
        @DisplayName("should throw DuplicateResourceException on duplicate key name")
        void shouldThrowOnDuplicateKeyName() {
            var user = createUser();
            var request = new CreateApiKeyRequest();
            request.setName("existing-key");

            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(apiKeyRepository.existsByTenantIdAndName(TENANT_ID, "existing-key")).willReturn(true);

            assertThatThrownBy(() -> authService.createApiKey(USER_ID.toString(), TENANT_ID, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            var request = new CreateApiKeyRequest();
            request.setName("my-key");
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.createApiKey(USER_ID.toString(), TENANT_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return user response when found")
        void shouldReturnUser() {
            var user = createUser();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            var result = authService.getCurrentUser(USER_ID.toString());

            assertThat(result.getUsername()).isEqualTo("testuser");
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenNotFound() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser(USER_ID.toString()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("revokeApiKey()")
    class RevokeApiKeyTests {

        @Test
        @DisplayName("should revoke API key successfully")
        void shouldRevokeKey() {
            var user = createUser();
            var apiKey = new ApiKey();
            apiKey.setId(UUID.randomUUID());
            apiKey.setUser(user);
            apiKey.setEnabled(true);

            given(apiKeyRepository.findById(apiKey.getId())).willReturn(Optional.of(apiKey));

            authService.revokeApiKey(apiKey.getId(), USER_ID.toString());

            then(apiKeyRepository).should().save(argThat(k -> !k.isEnabled()));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when key not found")
        void shouldThrowWhenKeyNotFound() {
            var keyId = UUID.randomUUID();
            given(apiKeyRepository.findById(keyId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.revokeApiKey(keyId, USER_ID.toString()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
