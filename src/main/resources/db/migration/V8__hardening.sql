-- V8: hardening — invariants the application already assumes, enforced by the database,
-- and a version column per mutable row for optimistic locking.
--
-- Deliberately absent:
--   * no CHECK on financial_accounts.opening_balance: an overdraft or card balance is negative;
--   * goal_contributions.amount is "<> 0", not "> 0": a withdrawal from a goal is negative;
--   * no new index on (user_id, occurred_on): idx_tx_user_date (V4) already is that index.

ALTER TABLE transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_tx_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT chk_tx_fx_rate_positive CHECK (fx_rate > 0),
    ADD CONSTRAINT chk_tx_base_amount_not_negative CHECK (base_amount >= 0),
    ADD CONSTRAINT chk_tx_transfer_amount_positive CHECK (transfer_amount IS NULL OR transfer_amount > 0),
    -- a transfer is one row with two different accounts; nothing else has a second account
    ADD CONSTRAINT chk_tx_transfer_shape CHECK ((type = 'TRANSFER') = (transfer_account_id IS NOT NULL)),
    ADD CONSTRAINT chk_tx_transfer_accounts_differ CHECK (transfer_account_id IS NULL OR transfer_account_id <> account_id),
    ADD CONSTRAINT chk_tx_transfer_no_category CHECK (type <> 'TRANSFER' OR category_id IS NULL);

ALTER TABLE financial_accounts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE categories
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE budgets
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_budget_amount_positive CHECK (amount > 0);

ALTER TABLE savings_goals
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_goal_target_positive CHECK (target_amount > 0);

ALTER TABLE goal_contributions
    ADD CONSTRAINT chk_contribution_not_zero CHECK (amount <> 0);

ALTER TABLE recurring_transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_recurring_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT chk_recurring_interval_positive CHECK (`interval` >= 1);

ALTER TABLE exchange_rates
    ADD CONSTRAINT chk_rate_positive CHECK (rate > 0);
