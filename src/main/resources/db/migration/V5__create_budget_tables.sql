-- V5: Budgets

CREATE TABLE budgets (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    category_id     VARCHAR(36)    NULL,
    amount          DECIMAL(18,4)  NOT NULL,
    period          VARCHAR(10)    NOT NULL DEFAULT 'MONTHLY',
    effective_from  DATE           NOT NULL,
    effective_to    DATE           NULL,
    archived_at     DATETIME(6)    NULL,
    deleted_at      DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_budget_user_cat_from (user_id, category_id, effective_from),
    CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
