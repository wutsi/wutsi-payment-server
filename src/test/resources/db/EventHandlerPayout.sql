INSERT INTO T_CONFIG(id, payment_method_provider, country, fees_percent, fees_value, payout_min_value, payout_max_value)
    VALUES
        (1, 1, 'CM', 0.01, 100, 1000, 1000000)
;

INSERT INTO T_BALANCE(account_id, payment_method_provider, synced, amount, currency, payout_id)
    VALUES
        (100, 1, '2020-02-01', 1000, 'XAF', '100'),
        (101, 1, '2020-02-01', 10000, 'XAF', '101'),
        (102, 1, '2020-02-01', 10000, 'XAF', '102'),
        (120, 1, '2020-02-01', 99, 'XAF', '120'),
        (121, 1, '2020-02-01', 1000001, 'XAF', '121'),
        (130, 2, '2020-02-01', 1000, 'XAF', '130'),

        (200, 1, '2020-02-01', 77777, 'XAF', '200')
;

INSERT INTO T_BALANCE(account_id, payment_method_provider, synced, amount, currency, payout_id)
    VALUES
        (1, 1, '2020-02-01', 100, 'XAF', '1'),
        (2, 2, '2020-02-01', 200, 'XAF', '2'),
        (3, 1, '2020-02-01', 0, 'XAF', '3'),
        (400, 1, '2020-02-01', 400, 'XAF', '400')
;

INSERT INTO T_PAYOUT(id, account_id, payment_method_token, amount, currency, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code)
    VALUES
        ('200', 1, 'xxxx', 77777, 'XAF', 1, '200', '200-xxx', null, null),
        ('201', 1, 'xxxx', 66666, 'XAF', 1, '201', '201-xxx', null, null),

        ('400', 1, 'xxxx', 400, 'XAF', 2, '400', null, null, null),
        ('401', 1, 'xxxx', 400, 'XAF', 2, '401', null, null, null),
        ('402', 1, 'xxxx', 400, 'XAF', 2, '402', null, null, null)
;
