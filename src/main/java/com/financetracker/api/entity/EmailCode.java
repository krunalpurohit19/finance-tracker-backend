package com.financetracker.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A one-time code emailed to a user. Only its hash is stored. */
@Entity
@Table(name = "email_codes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmailCode {

    public enum Purpose { RESET_PASSWORD, VERIFY_EMAIL }

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
