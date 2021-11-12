CREATE TABLE T_ACCOUNT(
    id                          SERIAL NOT NULL,
    tenant_id                   BIGINT NOT NULL,
    name                        VARCHAR(100),
    balance                     DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);

CREATE TABLE T_USER(
    id                          BIGINT NOT NULL,
    account_fk                  BIGINT NOT NULL REFERENCES T_ACCOUNT(id),

    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(account_fk),
    PRIMARY KEY(id)
);

CREATE TABLE T_GATEWAY(
    id                          SERIAL NOT NULL,

    code                        VARCHAR(10) NOT NULL,
    name                        VARCHAR(30) NOT NULL,
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(code),
    PRIMARY KEY(id)
);


CREATE TABLE T_TRANSACTION(
    id                          VARCHAR(36) NOT NULL,
    account_fk                  BIGINT NOT NULL REFERENCES T_ACCOUNT(id),
    gateway_fk                  BIGINT NOT NULL REFERENCES T_GATEWAY(id),

    type                        INT NOT NULL,
    payment_method_token        VARCHAR(36) NOT NULL,
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    description                 VARCHAR(100),
    status                      INT,
    gateway_transaction_id      VARCHAR(100),
    financial_transaction_id    VARCHAR(100),
    error_code                  VARCHAR(100),
    supplier_error_code         VARCHAR(100),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY(id)
);

CREATE TABLE T_RECORD(
    id                          SERIAL NOT NULL,
    transaction_fk              VARCHAR(36) NOT NULL REFERENCES T_TRANSACTION(id),
    account_fk                  BIGINT NOT NULL REFERENCES T_ACCOUNT(id),
    amount                      DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(id)
);
