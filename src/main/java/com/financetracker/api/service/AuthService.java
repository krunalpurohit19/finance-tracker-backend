package com.financetracker.api.service;

import com.financetracker.api.dto.auth.*;
import com.financetracker.api.entity.RefreshToken;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.*;
import com.financetracker.api.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final CategoryRepository categoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    /** System categories provisioned on sign-up, matching provisionUserDefaults. */
    private static final List<String[]> SYSTEM_EXPENSE_CATEGORIES = List.of(
            new String[]{"Food & Dining", "restaurant"},
            new String[]{"Transport", "car"},
            new String[]{"Housing", "home"},
            new String[]{"Utilities", "flash"},
            new String[]{"Entertainment", "film"},
            new String[]{"Shopping", "shopping-bag"},
            new String[]{"Health", "heart-pulse"},
            new String[]{"Education", "graduation-cap"},
            new String[]{"Personal", "user"},
            new String[]{"Other", "more-horizontal"}
    );

    private static final List<String[]> SYSTEM_INCOME_CATEGORIES = List.of(
            new String[]{"Salary", "briefcase"},
            new String[]{"Freelance", "laptop"},
            new String[]{"Investments", "trending-up"},
            new String[]{"Gifts", "gift"},
            new String[]{"Other", "more-horizontal"}
    );

    public AuthService(UserRepository userRepository, UserSettingsRepository settingsRepository,
                        CategoryRepository categoryRepository, RefreshTokenRepository refreshTokenRepository,
                        JwtTokenProvider jwtProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.categoryRepository = categoryRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("An account with that email already exists");
        }

        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .build();
        userRepository.save(user);

        provisionDefaults(userId);

        return generateTokens(user);
    }

    @Transactional
    public AuthResponse signIn(SignInRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> ApiException.unauthenticated("Invalid email or password"));

        if (user.getDeletedAt() != null) {
            throw ApiException.unauthenticated("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw ApiException.unauthenticated("Invalid email or password");
        }

        return generateTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        if (!jwtProvider.validateToken(request.getRefreshToken())) {
            throw ApiException.unauthenticated("Session expired. Sign in again.");
        }

        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> ApiException.unauthenticated("Session expired. Sign in again."));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw ApiException.unauthenticated("Session expired. Sign in again.");
        }

        User user = stored.getUser();
        refreshTokenRepository.delete(stored);
        return generateTokens(user);
    }

    @Transactional
    public void signOut(String userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private AuthResponse generateTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        RefreshToken entity = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .token(refreshToken)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtProvider.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(entity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .build())
                .build();
    }

    /** Provision defaults on sign-up: settings + system categories. Matches provisionUserDefaults. */
    private void provisionDefaults(String userId) {
        UserSettings settings = UserSettings.builder().userId(userId).build();
        settingsRepository.save(settings);

        int order = 0;
        for (String[] cat : SYSTEM_EXPENSE_CATEGORIES) {
            categoryRepository.save(buildSystemCategory(userId, cat[0], cat[1], CategoryKind.EXPENSE, order++));
        }
        order = 0;
        for (String[] cat : SYSTEM_INCOME_CATEGORIES) {
            categoryRepository.save(buildSystemCategory(userId, cat[0], cat[1], CategoryKind.INCOME, order++));
        }
    }

    private Category buildSystemCategory(String userId, String name, String icon, CategoryKind kind, int sortOrder) {
        User userRef = User.builder().id(userId).build();
        return Category.builder()
                .id(UUID.randomUUID().toString())
                .user(userRef)
                .name(name)
                .icon(icon)
                .kind(kind)
                .isSystem(true)
                .sortOrder(sortOrder)
                .build();
    }
}
