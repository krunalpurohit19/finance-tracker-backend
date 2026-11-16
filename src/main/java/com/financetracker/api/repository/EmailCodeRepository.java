package com.financetracker.api.repository;

import com.financetracker.api.entity.EmailCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface EmailCodeRepository extends JpaRepository<EmailCode, String> {

    /**
     * The one live code, row-locked (FOR UPDATE) so simultaneous guesses are counted one after the other
     * instead of all reading the same attempt count.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailCode> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String userId, EmailCode.Purpose purpose);

    /** A new code, or a successful use, retires every other code of that purpose. */
    @Modifying
    @Query("UPDATE EmailCode c SET c.usedAt = :now WHERE c.user.id = :userId AND c.purpose = :purpose AND c.usedAt IS NULL")
    void retireAll(String userId, EmailCode.Purpose purpose, Instant now);

    long countByUserIdAndPurposeAndCreatedAtAfter(String userId, EmailCode.Purpose purpose, Instant after);
}
