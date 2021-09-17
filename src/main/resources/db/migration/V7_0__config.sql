CREATE TABLE T_CONFIG(
    id                          BIGINT NOT NULL,
    payment_method_provider     INT NOT NULL DEFAULT 0,
    country                     VARCHAR(2),
    fees_percent                DECIMAL(20, 4) NOT NULL DEFAULT 0,
    fees_value                  DECIMAL(20, 4) NOT NULL DEFAULT 0,
    payout_min_value            DECIMAL(20, 4) NOT NULL DEFAULT 0,
    payout_max_value            DECIMAL(20, 4) NOT NULL DEFAULT 0,

    updated                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(payment_method_provider, country),
    PRIMARY KEY (id)
);
