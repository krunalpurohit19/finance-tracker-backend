package com.financetracker.api.entity;

import com.financetracker.api.entity.enums.ThemePreference;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "base_currency", nullable = false, length = 3)
    @Builder.Default
    private String baseCurrency = "INR";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String locale = "en-IN";

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @Column(name = "date_format", nullable = false, length = 20)
    @Builder.Default
    private String dateFormat = "dd/MM/yyyy";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ThemePreference theme = ThemePreference.SYSTEM;

    @Column(name = "week_starts_on", nullable = false)
    @Builder.Default
    private int weekStartsOn = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
