DROP TABLE T_CHARGE;
CREATE TABLE T_CHARGE(
    id                          VARCHAR(36) NOT NULL,
    merchant_id                 BIGINT NOT NULL,
    customer_id                 BIGINT NOT NULL,
    user_id                     BIGINT NOT NULL,
    application_id              BIGINT NOT NULL,
    payment_method_token        VARCHAR(100) NOT NULL,
    payment_method_type         INT NOT NULL DEFAULT 0,
    payment_method_provider     INT NOT NULL DEFAULT 0,
    external_id                 VARCHAR(100) NOT NULL,
    description                 VARCHAR(100) NOT NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    status                      INT,
    gateway_transaction_id      VARCHAR(100) NOT NULL,
    error_code                  INT,
    supplier_error_code         VARCHAR(100),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);

DROP TABLE T_TRANSACTION;
CREATE TABLE T_TRANSACTION(
    id                          VARCHAR(36) NOT NULL,
    type                        INT NOT NULL DEFAULT 0,
    status                      INT,
    from_account_id             BIGINT NOT NULL,
    to_account_id               BIGINT NOT NULL,
    description                 VARCHAR(100) NOT NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    fees                        DECIMAL(20, 4) NOT NULL DEFAULT 0,
    net                         DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    financial_transaction_id    VARCHAR(100),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);