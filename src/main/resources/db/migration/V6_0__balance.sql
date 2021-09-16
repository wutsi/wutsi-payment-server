ALTER TABLE T_TRANSACTION DROP COLUMN from_account_id;
ALTER TABLE T_TRANSACTION RENAME COLUMN to_account_id TO account_id;
ALTER TABLE T_TRANSACTION ADD COLUMN payment_method_provider INT NOT NULL DEFAULT 0;

CREATE INDEX T_TRANSACTION__account_created ON T_TRANSACTION(account_id, created);

CREATE TABLE T_BALANCE(
    id                          SERIAL NOT NULL,
    account_id                  BIGINT NOT NULL,
    payment_method_provider     INT NOT NULL DEFAULT 0,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3) NOT NULL,
    synced                      DATE NOT NULL,
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(account_id, payment_method_provider),
    PRIMARY KEY(id)
);

