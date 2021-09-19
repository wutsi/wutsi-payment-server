INSERT INTO T_CONFIG(id, payment_method_provider, country, fees_percent, fees_value, payout_min_value, payout_max_value)
    VALUES
        (1, 1, 'CM', 0.01, 100, 1000, 1000000)
;

INSERT INTO T_CHARGE(id, merchant_id, customer_id, user_id, application_id, payment_method_token, payment_method_provider, payment_method_type, external_id, amount, currency, status, gateway_transaction_id, error_code, supplier_error_code, description)
    VALUES
        ('100', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 2, '100-0000', null, null, 'Sample charge'),
        ('101', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 2, '101-0000', null, null, 'Sample charge'),
        ('102', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 2, '102-0000', null, null, 'Sample charge'),

        ('200', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 1, '200-0000', null, '200-1111', 'Sample charge'),
        ('201', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 1, '201-0000', null, '201-1111', 'Sample charge')

;

INSERT INTO T_TRANSACTION(reference_id, account_id, payment_method_provider, description, currency, amount, created)
    VALUES
        ('201', 1, 1, 'yo', 'XAF', 9900, now()),
        ('201', 1, 1, null, 'XAF', 100, now()),

        ('301', 3, 1, null, 'XAF', 100, '2020-02-01'),
        ('302', 3, 1, null, 'XAF', -200, '2020-02-02'),
        ('303', 3, 1, null, 'XAF', 300, '2020-02-03'),
        ('304', 3, 1, null, 'XAF', 400, '2020-02-04'),
        ('305', 3, 1, null, 'XAF', 1000, '2020-01-30'),
        ('306', 3, 1, null, 'XAF', 2000, now()),

        ('401', 4, 1, null, 'XAF', 40, '2021-01-01'),
        ('402', 4, 1, null, 'XAF', 50, '2021-01-02'),
        ('403', 4, 1, null, 'XAF', 100, now())
;
