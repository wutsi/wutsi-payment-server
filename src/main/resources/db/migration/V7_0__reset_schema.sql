ALTER TABLE T_TRANSACTION
    ADD COLUMN fees DECIMAL(20, 4) NOT NULL DEFAULT 0;
ALTER TABLE T_TRANSACTION
    ADD COLUMN net DECIMAL(20, 4) NOT NULL DEFAULT 0;

CREATE TABLE T_BALANCE
(
    id        SERIAL         NOT NULL,

    user_id   BIGINT         NOT NULL,
    tenant_id BIGINT         NOT NULL,
    amount    DECIMAL(20, 4) NOT NULL DEFAULT 0
        CONSTRAINT positive_balance CHECK (amount >= 0),
    currency  VARCHAR(3),
    created   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated   TIMESTAMPTZ    NOT NULL DEFAULT now(),

    UNIQUE (user_id, tenant_id),
    PRIMARY KEY (id)
);

CREATE
OR REPLACE FUNCTION balance_updated()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated
= NOW();
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trigger_balance_updated
    BEFORE UPDATE
    ON T_balance
    FOR EACH ROW
    EXECUTE PROCEDURE balance_updated();

INSERT INTO T_BALANCE(user_id, tenant_id, amount, currency)
SELECT user_fk, 1, balance, currency
FROM T_USER_ACCOUNT UA
         JOIN T_ACCOUNT A ON UA.account_fk = A.id;