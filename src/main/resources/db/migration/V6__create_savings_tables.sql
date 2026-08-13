-- V6: Savings goals and contributions

CREATE TABLE savings_goals (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    target_amount   DECIMAL(18,4)  NOT NULL,
    target_date     DATE           NULL,
    account_id      VARCHAR(36)    NULL,
    color           VARCHAR(7)     NULL,
    icon            VARCHAR(40)    NULL,
    status          VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    archived_at     DATETIME(6)    NULL,
    deleted_at      DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_goal_user_status (user_id, status),
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_account FOREIGN KEY (account_id) REFERENCES financial_accounts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE goal_contributions (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    goal_id         VARCHAR(36)    NOT NULL,
    amount          DECIMAL(18,4)  NOT NULL,
    occurred_on     DATE           NOT NULL,
    transaction_id  VARCHAR(36)    NULL,
    notes           TEXT           NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)    NULL,
    UNIQUE KEY uk_contribution_tx (transaction_id),
    INDEX idx_contribution_goal_date (goal_id, occurred_on),
    CONSTRAINT fk_contribution_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_contribution_goal FOREIGN KEY (goal_id) REFERENCES savings_goals(id) ON DELETE CASCADE,
    CONSTRAINT fk_contribution_tx FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
