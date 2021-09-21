INSERT INTO T_CONFIG(id, payment_method_provider, country, fees_percent, fees_value, payout_min_value, payout_max_value)
    VALUES
        (1, 1, 'CM', 0.01, 100, 1000, 1000000),
        (2, 2, 'CM', 0.01, 100, 1000, 1000000)
;

INSERT INTO T_BALANCE(account_id, payment_method_provider, synced, amount, currency, payout_id)
    VALUES
        (100, 1, '2020-02-01', 70000, 'XAF', '100'),
        (101, 1, '2020-02-01', 1000, 'XAF', '101'),
        (102, 1, '2020-02-01', 1000001, 'XAF', '102'),

        (200, 1, '2020-02-01', 100, 'XAF', '200'),
        (201, 1, '2020-02-01', 10000, 'XAF', '201'),

        (300, 1, '2020-02-01', 10000, 'XAF', '300'),

        (400, 1, '2020-02-01', 40000, 'XAF', '400')
;

INSERT INTO T_PAYOUT(id, account_id, user_id, payment_method_token, payment_method_type, payment_method_provider, amount, currency, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code)
    VALUES
        ('400', 400, 400, 'xxxx', 1, 2, 400, 'XAF', 1, '400-gw', '400-fin', null, null)
;