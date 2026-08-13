-- V2: Settings & currency tables

CREATE TABLE user_settings (
    user_id       VARCHAR(36) NOT NULL PRIMARY KEY,
    base_currency CHAR(3)     NOT NULL DEFAULT 'INR',
    locale        VARCHAR(20) NOT NULL DEFAULT 'en-IN',
    timezone      VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    date_format   VARCHAR(20) NOT NULL DEFAULT 'dd/MM/yyyy',
    theme         VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',
    week_starts_on INT        NOT NULL DEFAULT 1,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE exchange_rates (
    id             VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id        VARCHAR(36)    NOT NULL,
    from_currency  CHAR(3)        NOT NULL,
    to_currency    CHAR(3)        NOT NULL,
    rate           DECIMAL(18,8)  NOT NULL,
    effective_from DATE           NOT NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at     DATETIME(6)    NULL,
    UNIQUE KEY uk_exchange_rates (user_id, from_currency, to_currency, effective_from),
    INDEX idx_exchange_rates_lookup (user_id, from_currency, to_currency, effective_from DESC),
    CONSTRAINT fk_exchange_rates_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
