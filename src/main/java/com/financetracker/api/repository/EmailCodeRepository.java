package com.financetracker.api.repository;

import com.financetracker.api.entity.EmailCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EmailCodeRepository extends JpaRepository<EmailCode, String> {

    /** The newest unused code: requesting a new one supersedes the old. */
    Optional<EmailCode> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String userId, EmailCode.Purpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(String userId, EmailCode.Purpose purpose, Instant after);
}
