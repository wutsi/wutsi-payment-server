CREATE TABLE T_PAYMENT_REQUEST(
    id                          VARCHAR(36) NOT NULL,
    account_id                  BIGINT NOT NULL,
    tenant_id                   BIGINT NOT NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    description                 VARCHAR(100),
    invoice_id                  VARCHAR(36),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires                     TIMESTAMPTZ,

    PRIMARY KEY(id)
);
