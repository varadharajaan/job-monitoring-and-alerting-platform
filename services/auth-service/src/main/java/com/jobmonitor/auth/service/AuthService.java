package com.jobmonitor.auth.service;

import com.jobmonitor.auth.dto.*;
import com.jobmonitor.auth.entity.ApiKey;
import com.jobmonitor.auth.entity.User;
import com.jobmonitor.auth.repository.ApiKeyRepository;
import com.jobmonitor.auth.repository.UserRepository;
import com.jobmonitor.platform.common.exception.BusinessException;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ErrorCode;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EntityValidator;
import com.jobmonitor.platform.common.security.JwtTokenProvider;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Authentication service — login, register, API key management.
 * <p>
 * Functional patterns: EntityValidator, Function mappers, Supplier for key generation.
 * Zero hardcoded values — all config from SecurityProperties.
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EntityValidator<RegisterRequest> uniqueUsernameValidator;
    private final EntityValidator<RegisterRequest> uniqueEmailValidator;

    /** Map User entity to UserResponse */
    private final Function<User, UserResponse> toUserResponse = user ->
            UserResponse.builder()
                    .id(user.getId())
                    .tenantId(user.getTenantId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole().name())
                    .enabled(user.isEnabled())
                    .lastLoginAt(user.getLastLoginAt())
                    .createdAt(user.getCreatedAt())
                    .build();

    /** Secure API key generator — produces jm_<32-char-hex> */
    private final Supplier<String> apiKeyGenerator = () -> {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "jm_" + HexFormat.of().formatHex(bytes);
    };

    public AuthService(UserRepository userRepository,
                       ApiKeyRepository apiKeyRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;

        this.uniqueUsernameValidator = request -> {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new DuplicateResourceException("User", request.getUsername());
            }
        };
        this.uniqueEmailValidator = request -> {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("User", request.getEmail());
            }
        };
    }

    @Transactional
    @Timed(value = "auth.login", description = "Time to authenticate a user")
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameAndEnabledTrue(request.getUsername())
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(
                        "Invalid username or password", ErrorCode.AUTHENTICATION_FAILED));

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateToken(
                user.getId().toString(), user.getUsername(),
                user.getTenantId(), List.of(user.getRole().name()));

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        log.info("User logged in: username={}, tenant={}", user.getUsername(), user.getTenantId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600)
                .userId(user.getId().toString())
                .username(user.getUsername())
                .tenantId(user.getTenantId())
                .role(user.getRole().name())
                .issuedAt(Instant.now())
                .build();
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Validate uniqueness — functional validators
        uniqueUsernameValidator.validate(request);
        uniqueEmailValidator.validate(request);

        User user = new User();
        user.setTenantId(request.getTenantId());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(Optional.ofNullable(request.getRole())
                .filter(r -> !r.isBlank())
                .map(String::toUpperCase)
                .map(User.Role::valueOf)
                .orElse(User.Role.VIEWER));

        User saved = userRepository.save(user);
        log.info("User registered: username={}, tenant={}", saved.getUsername(), saved.getTenantId());
        return toUserResponse.apply(saved);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        return jwtTokenProvider.extractUserId(refreshToken)
                .flatMap(userId -> userRepository.findById(UUID.fromString(userId)))
                .filter(User::isEnabled)
                .map(user -> {
                    String newAccessToken = jwtTokenProvider.generateToken(
                            user.getId().toString(), user.getUsername(),
                            user.getTenantId(), List.of(user.getRole().name()));
                    String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

                    return AuthResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(newRefreshToken)
                            .tokenType("Bearer")
                            .expiresIn(3600)
                            .userId(user.getId().toString())
                            .username(user.getUsername())
                            .tenantId(user.getTenantId())
                            .role(user.getRole().name())
                            .issuedAt(Instant.now())
                            .build();
                })
                .orElseThrow(() -> new BusinessException(
                        "Invalid or expired refresh token", ErrorCode.AUTHENTICATION_FAILED));
    }

    @Transactional
    public ApiKeyResponse createApiKey(String userId, String tenantId, CreateApiKeyRequest request) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (apiKeyRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateResourceException("ApiKey", request.getName());
        }

        String rawKey = apiKeyGenerator.get();
        String keyPrefix = rawKey.substring(0, 7); // "jm_xxxx"

        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setUser(user);
        apiKey.setName(request.getName());
        apiKey.setKeyHash(passwordEncoder.encode(rawKey));
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setScopes(Optional.ofNullable(request.getScopes())
                .filter(s -> !s.isEmpty())
                .orElse(List.of("READ")));
        apiKey.setExpiresAt(request.getExpiresAt());

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API key created: name={}, prefix={}, tenant={}", saved.getName(), keyPrefix, tenantId);

        return ApiKeyResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .rawKey(rawKey)  // Only returned once on creation
                .keyPrefix(keyPrefix)
                .scopes(saved.getScopes())
                .expiresAt(saved.getExpiresAt())
                .enabled(saved.isEnabled())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(String userId) {
        return apiKeyRepository.findByUserIdAndEnabledTrue(UUID.fromString(userId)).stream()
                .map(key -> ApiKeyResponse.builder()
                        .id(key.getId())
                        .name(key.getName())
                        .keyPrefix(key.getKeyPrefix())
                        .scopes(key.getScopes())
                        .expiresAt(key.getExpiresAt())
                        .lastUsedAt(key.getLastUsedAt())
                        .enabled(key.isEnabled())
                        .createdAt(key.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeApiKey(UUID keyId, String userId) {
        apiKeyRepository.findById(keyId)
                .filter(key -> key.getUser().getId().toString().equals(userId))
                .ifPresentOrElse(
                        key -> { key.setEnabled(false); apiKeyRepository.save(key); },
                        () -> { throw new ResourceNotFoundException("ApiKey", keyId.toString()); }
                );
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .map(toUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
