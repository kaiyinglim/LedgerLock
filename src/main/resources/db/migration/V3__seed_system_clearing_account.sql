-- This single-currency model has one internal clearing account. Customer rows are unaffected.
CREATE UNIQUE INDEX accounts_one_system_account
    ON accounts (account_type) WHERE account_type = 'SYSTEM';

INSERT INTO accounts (owner_name, account_type, balance_sen)
VALUES ('System clearing', 'SYSTEM', 0);
