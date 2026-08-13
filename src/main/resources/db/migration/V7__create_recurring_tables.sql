-- V7: Recurring transactions

CREATE TABLE recurring_transactions (
    id                    VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id               VARCHAR(36)    NOT NULL,
    name                  VARCHAR(255)   NOT NULL,
    type                  VARCHAR(10)    NOT NULL,
    amount                DECIMAL(18,4)  NOT NULL,
    currency              CHAR(3)        NOT NULL,
    account_id            VARCHAR(36)    NOT NULL,
    transfer_account_id   VARCHAR(36)    NULL,
    category_id           VARCHAR(36)    NULL,
    merchant              VARCHAR(255)   NULL,
    notes                 TEXT           NULL,
    frequency             VARCHAR(10)    NOT NULL,
    `interval`            INT            NOT NULL DEFAULT 1,
    start_on              DATE           NOT NULL,
    end_on                DATE           NULL,
    day_of_month          INT            NULL,
    weekday               INT            NULL,
    next_occurrence        DATE           NOT NULL,
    last_generated        DATE           NULL,
    auto_post             TINYINT(1)     NOT NULL DEFAULT 1,
    is_active             TINYINT(1)     NOT NULL DEFAULT 1,
    archived_at           DATETIME(6)    NULL,
    deleted_at            DATETIME(6)    NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_rec_user_active_next (user_id, is_active, next_occurrence),
    CONSTRAINT fk_rec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_account FOREIGN KEY (account_id) REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_transfer_account FOREIGN KEY (transfer_account_id) REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add the FK from transactions to recurring_transactions (deferred because of circular dependency)
ALTER TABLE transactions
    ADD CONSTRAINT fk_tx_recurring FOREIGN KEY (recurring_id) REFERENCES recurring_transactions(id) ON DELETE SET NULL;
