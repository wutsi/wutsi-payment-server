INSERT INTO T_TRANSACTION(reference_id, account_id, payment_method_provider, description, currency, amount, created)
    VALUES
        ('201', 2, 1, 'yo', 'XAF', 9900, '2020-01-01'),
        ('202', 2, 1, null, 'XAF', 100, '2020-02-22'),
        ('203', 2, 1, null, 'XAF', 7777, now()),

        ('301', 3, 1, null, 'XAF', 100, '2020-02-01'),
        ('302', 3, 1, null, 'XAF', -200, '2020-02-02'),
        ('303', 3, 1, null, 'XAF', 300, '2020-02-03'),
        ('304', 3, 1, null, 'XAF', 400, '2020-02-04'),
        ('305', 3, 1, null, 'XAF', 1000, '2020-01-30'),
        ('306', 3, 1, null, 'XAF', 2000, now()),

        ('301', 3, 2, null, 'XAF', 100, '2020-02-01'),
        ('302', 3, 2, null, 'XAF', -200, '2020-02-02')
;

INSERT INTO T_BALANCE(account_id, payment_method_provider, synced, amount, currency, payout_id)
    VALUES
        (3, 1, '2020-02-01', 100, 'XAF', '3333'),
        (4, 1, '2020-02-01', 0, 'XAF', '4444')
;