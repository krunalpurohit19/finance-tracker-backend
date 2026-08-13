-- V4: Transactions — the ledger

CREATE TABLE transactions (
    id                    VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id               VARCHAR(36)    NOT NULL,
    type                  VARCHAR(10)    NOT NULL,
    amount                DECIMAL(18,4)  NOT NULL,
    currency              CHAR(3)        NOT NULL,
    base_amount           DECIMAL(18,4)  NOT NULL,
    base_currency         CHAR(3)        NOT NULL,
    fx_rate               DECIMAL(18,8)  NOT NULL DEFAULT 1.00000000,
    account_id            VARCHAR(36)    NOT NULL,
    transfer_account_id   VARCHAR(36)    NULL,
    transfer_amount       DECIMAL(18,4)  NULL,
    transfer_currency     CHAR(3)        NULL,
    category_id           VARCHAR(36)    NULL,
    occurred_on           DATE           NOT NULL,
    merchant              VARCHAR(255)   NULL,
    notes                 TEXT           NULL,
    source                VARCHAR(10)    NOT NULL DEFAULT 'MANUAL',
    external_id           VARCHAR(255)   NULL,
    recurring_id          VARCHAR(36)    NULL,
    occurrence_on         DATE           NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at            DATETIME(6)    NULL,

    UNIQUE KEY uk_tx_recurring_occurrence (recurring_id, occurrence_on),
    INDEX idx_tx_user_date (user_id, occurred_on DESC),
    INDEX idx_tx_user_acct_date (user_id, account_id, occurred_on DESC),
    INDEX idx_tx_user_cat_date (user_id, category_id, occurred_on DESC),
    INDEX idx_tx_user_type_date (user_id, type, occurred_on DESC),
    INDEX idx_tx_transfer_acct_date (transfer_account_id, occurred_on DESC),

    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_transfer_account FOREIGN KEY (transfer_account_id) REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
