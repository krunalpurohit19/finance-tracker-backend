package com.financetracker.api.repository;

import com.financetracker.api.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, String> {

    @Query("""
        SELECT e FROM ExchangeRate e
        WHERE e.user.id = :userId AND e.deletedAt IS NULL
        ORDER BY e.fromCurrency, e.toCurrency, e.effectiveFrom DESC
    """)
    List<ExchangeRate> findActiveByUserId(String userId);

    Optional<ExchangeRate> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    /** The rate in force on a given date: newest row whose effectiveFrom <= date. */
    @Query("""
        SELECT e FROM ExchangeRate e
        WHERE e.user.id = :userId AND e.deletedAt IS NULL
          AND e.fromCurrency = :from AND e.toCurrency = :to
          AND e.effectiveFrom <= :asOf
        ORDER BY e.effectiveFrom DESC
        LIMIT 1
    """)
    Optional<ExchangeRate> findEffectiveRate(String userId, String from, String to, LocalDate asOf);

    /** Upsert: find existing for same user/pair/date. */
    Optional<ExchangeRate> findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFromAndDeletedAtIsNull(
            String userId, String fromCurrency, String toCurrency, LocalDate effectiveFrom);
}
