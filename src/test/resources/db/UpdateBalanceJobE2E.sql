INSERT INTO T_CONFIG(id, payment_method_provider, country, fees_percent, fees_value, payout_min_value, payout_max_value)
    VALUES
        (1, 1, 'CM', 0.01, 100, 100, 1000000)
;

INSERT INTO T_TRANSACTION(reference_id, account_id, payment_method_provider, description, currency, amount, created)
    VALUES
        ('301', 3, 1, null, 'XAF', 100, '2020-02-01'),
        ('302', 3, 1, null, 'XAF', -200, '2020-02-02'),
        ('303', 3, 1, null, 'XAF', 300, '2020-02-03'),
        ('304', 3, 1, null, 'XAF', 400, '2020-02-04'),
        ('305', 3, 1, null, 'XAF', 1000, '2020-01-30'),
        ('306', 3, 1, null, 'XAF', 2000, now())
;

INSERT INTO T_BALANCE(account_id, payment_method_provider, synced, amount, currency, payout_id)
    VALUES
        (3, 1, '2020-02-01', 100, 'XAF', '3333')
;
