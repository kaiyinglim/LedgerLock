CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    balance_sen BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_owner_name_not_blank CHECK (owner_name ~ '[^[:space:]]'),
    CONSTRAINT accounts_valid_type CHECK (account_type IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT accounts_customer_balance_nonnegative
        CHECK (account_type = 'SYSTEM' OR balance_sen >= 0)
);
