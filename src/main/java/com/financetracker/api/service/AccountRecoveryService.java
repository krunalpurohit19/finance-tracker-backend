package com.financetracker.api.service;

import com.financetracker.api.dto.auth.RecoveryDtos.ResetPasswordRequest;
import com.financetracker.api.entity.EmailCode;
import com.financetracker.api.entity.EmailCode.Purpose;
import com.financetracker.api.entity.User;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.EmailCodeRepository;
import com.financetracker.api.repository.RefreshTokenRepository;
import com.financetracker.api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Password reset and email verification with a 6-digit code sent by email. A code lasts 15 minutes,
 * allows 5 wrong guesses, works once, and a user gets at most 5 per hour.
 */
@Service
public class AccountRecoveryService {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 5;
    private static final String BAD_CODE = "That code is wrong or has expired. Request a new one.";

    private final UserRepository userRepo;
    private final EmailCodeRepository codeRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final Mailer mailer;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryService(UserRepository userRepo, EmailCodeRepository codeRepo,
                                  RefreshTokenRepository refreshTokenRepo, PasswordEncoder passwordEncoder, Mailer mailer) {
        this.userRepo = userRepo;
        this.codeRepo = codeRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
    }

    /** Answers the same whether or not the address has an account. */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepo.findByEmail(email).filter(u -> u.getDeletedAt() == null).ifPresent(user ->
                issue(user, Purpose.RESET_PASSWORD, "Your password reset code",
                        "Use this code to reset your Finance Tracker password: %s\n\n"
                                + "It expires in 15 minutes. If you didn't ask for it, ignore this email."));
    }

    /** A wrong guess is counted even though the request fails, hence noRollbackFor. */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest req) {
        User user = userRepo.findByEmail(req.email()).filter(u -> u.getDeletedAt() == null)
                .orElseThrow(AccountRecoveryService::badCode);
        redeem(user, Purpose.RESET_PASSWORD, req.code());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setEmailVerified(true); // they just proved they read this mailbox
        refreshTokenRepo.deleteAllByUserId(user.getId()); // sign out every device
    }

    @Transactional
    public void sendVerification(String userId) {
        User user = userRepo.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isEmailVerified()) return;
        issue(user, Purpose.VERIFY_EMAIL, "Confirm your email",
                "Your Finance Tracker verification code: %s\n\nIt expires in 15 minutes.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(String userId, String code) {
        User user = userRepo.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isEmailVerified()) return;
        redeem(user, Purpose.VERIFY_EMAIL, code);
        user.setEmailVerified(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void issue(User user, Purpose purpose, String subject, String body) {
        if (codeRepo.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose,
                Instant.now().minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            return; // silently: the caller must not learn anything from the answer
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        String id = UUID.randomUUID().toString();
        codeRepo.save(EmailCode.builder().id(id).user(user).purpose(purpose).codeHash(hash(id, code))
                .expiresAt(Instant.now().plus(CODE_LIFETIME)).build());
        mailer.send(user.getEmail(), subject, body.formatted(code));
    }

    private void redeem(User user, Purpose purpose, String code) {
        EmailCode stored = codeRepo.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()) && c.getAttempts() < MAX_ATTEMPTS)
                .orElseThrow(AccountRecoveryService::badCode);
        if (!MessageDigest.isEqual(stored.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(stored.getId(), code).getBytes(StandardCharsets.US_ASCII))) {
            stored.setAttempts(stored.getAttempts() + 1);
            throw badCode();
        }
        stored.setUsedAt(Instant.now());
    }

    /** Salted with the row id; the code is short-lived and guess-limited, so a fast hash is enough. */
    static String hash(String id, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((id + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException badCode() {
        return ApiException.validationFailed(BAD_CODE, Map.of("code", List.of(BAD_CODE)));
    }
}
