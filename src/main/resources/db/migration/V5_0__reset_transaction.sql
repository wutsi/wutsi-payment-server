DROP TABLE T_TRANSACTION;
CREATE TABLE T_TRANSACTION(
    id                          SERIAL NOT NULL,
    reference_id                VARCHAR(36) NOT NULL,
    type                        INT NOT NULL DEFAULT 0,
    from_account_id             BIGINT NOT NULL,
    to_account_id               BIGINT NOT NULL,
    description                 VARCHAR(100) NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3) NOT NULL,
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);

CREATE INDEX T_TRANSACTION__reference_id ON T_TRANSACTION(reference_id);
