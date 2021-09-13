ALTER TABLE T_CHARGE ADD COLUMN financial_transaction_id VARCHAR(100);
ALTER TABLE T_CHARGE ALTER COLUMN status SET NOT NULL;
ALTER TABLE T_CHARGE ALTER COLUMN status SET DEFAULT 0;
CREATE UNIQUE INDEX T_CHARGE__gateway_transaction_id ON T_CHARGE(payment_method_provider, gateway_transaction_id);

ALTER TABLE T_CHARGE ADD COLUMN updated TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE OR REPLACE FUNCTION charge_updated()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_charge_updated
BEFORE UPDATE ON T_CHARGE
FOR EACH ROW
EXECUTE PROCEDURE charge_updated();


DROP TABLE T_TRANSACTION;
CREATE TABLE T_TRANSACTION(
    id                          VARCHAR(36) NOT NULL,
    type                        INT NOT NULL DEFAULT 0,
    from_account_id             BIGINT NOT NULL,
    to_account_id               BIGINT NOT NULL,
    description                 VARCHAR(100) NOT NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    fees                        DECIMAL(20, 4) NOT NULL DEFAULT 0,
    net                         DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3) NOT NULL,
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);
