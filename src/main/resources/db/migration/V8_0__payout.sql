ALTER TABLE T_BALANCE ADD COLUMN payout_id VARCHAR(36) NOT NULL;
CREATE UNIQUE INDEX T_BALANCE__payout_id ON T_BALANCE(payout_id);

CREATE TABLE T_PAYOUT(
    id                          VARCHAR(36) NOT NULL,
    account_id                  BIGINT NOT NULL,
    user_id                     BIGINT NULL,
    payment_method_token        VARCHAR(100) NOT NULL,
    payment_method_type         INT NOT NULL DEFAULT 0,
    payment_method_provider     INT NOT NULL DEFAULT 0,
    description                 VARCHAR(100) NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    status                      INT,
    gateway_transaction_id      VARCHAR(100) NOT NULL,
    financial_transaction_id    VARCHAR(100) NULL,
    error_code                  INT,
    supplier_error_code         VARCHAR(100),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);

CREATE OR REPLACE FUNCTION payout_updated()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_payout_updated
BEFORE UPDATE ON T_PAYOUT
FOR EACH ROW
EXECUTE PROCEDURE payout_updated();
