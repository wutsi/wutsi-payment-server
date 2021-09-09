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
    error_code                  VARCHAR(100),
    supplier_error_code         VARCHAR(100),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);

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
    gateway_transaction_id      VARCHAR(100) NOT NULL,
    financial_transaction_id    VARCHAR(100),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);
