-- V3: Financial accounts and categories

CREATE TABLE financial_accounts (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    type            VARCHAR(20)    NOT NULL,
    class           VARCHAR(10)    NOT NULL,
    currency        CHAR(3)        NOT NULL,
    opening_balance DECIMAL(18,4)  NOT NULL DEFAULT 0.0000,
    institution     VARCHAR(255)   NULL,
    last4           VARCHAR(4)     NULL,
    color           VARCHAR(7)     NULL,
    icon            VARCHAR(40)    NULL,
    is_default      TINYINT(1)     NOT NULL DEFAULT 0,
    sort_order      INT            NOT NULL DEFAULT 0,
    archived_at     DATETIME(6)    NULL,
    deleted_at      DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_fin_acct_user_archived (user_id, archived_at),
    INDEX idx_fin_acct_user_sort (user_id, sort_order),
    CONSTRAINT fk_fin_acct_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    parent_id   VARCHAR(36)  NULL,
    name        VARCHAR(255) NOT NULL,
    kind        VARCHAR(10)  NOT NULL,
    color       VARCHAR(7)   NULL,
    icon        VARCHAR(40)  NULL,
    is_system   TINYINT(1)   NOT NULL DEFAULT 0,
    sort_order  INT          NOT NULL DEFAULT 0,
    archived_at DATETIME(6)  NULL,
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_cat_user_kind (user_id, kind, archived_at),
    CONSTRAINT fk_cat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
