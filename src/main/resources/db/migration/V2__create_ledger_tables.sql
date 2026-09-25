CREATE TABLE ledger_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ledger_transactions_valid_type CHECK (transaction_type IN ('DEPOSIT', 'TRANSFER'))
);

CREATE TABLE ledger_entries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ledger_transaction_id BIGINT NOT NULL REFERENCES ledger_transactions (id),
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    amount_sen BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ledger_entries_nonzero_amount CHECK (amount_sen <> 0),
    CONSTRAINT ledger_entries_one_per_account UNIQUE (ledger_transaction_id, account_id)
);

CREATE INDEX ledger_entries_account_id ON ledger_entries (account_id);
