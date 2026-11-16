-- V9: account safety — emailed one-time codes (password reset, email verification) and
-- an idempotency key so a retried "create transaction" can't post twice.

CREATE TABLE email_codes (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    purpose     VARCHAR(20)  NOT NULL,           -- RESET_PASSWORD | VERIFY_EMAIL
    code_hash   CHAR(64)     NOT NULL,           -- SHA-256 hex; the code itself is never stored
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_email_codes_user (user_id, purpose, created_at),
    CONSTRAINT fk_email_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE transactions
    ADD COLUMN idempotency_key VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_tx_user_idempotency (user_id, idempotency_key);
