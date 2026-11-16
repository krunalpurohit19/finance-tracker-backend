package com.financetracker.api.repository;

import com.financetracker.api.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {

    /**
     * For anything that writes money in the base currency (transactions): SELECT ... FOR SHARE, so a
     * base-currency change waits for the write and then sees it. Must be the transaction's first read.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM UserSettings s WHERE s.userId = :userId")
    Optional<UserSettings> findForShare(String userId);

    /** For changing the base currency: SELECT ... FOR UPDATE, waits for in-flight writers. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSettings s WHERE s.userId = :userId")
    Optional<UserSettings> findForUpdate(String userId);
}
