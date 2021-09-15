INSERT INTO T_TRANSACTION(reference_id, account_id, description, currency, amount, created)
    VALUES
        ('201', 2, 'yo', 'XAF', 9900, '2020-01-01'),
        ('202', 2, null, 'XAF', 100, '2020-02-22'),
        ('203', 2, null, 'XAF', 7777, now()),

        ('301', 3, null, 'XAF', 100, '2020-02-01'),
        ('302', 3, null, 'XAF', -200, '2020-02-02'),
        ('303', 3, null, 'XAF', 300, '2020-02-03'),
        ('304', 3, null, 'XAF', 400, '2020-02-04'),
        ('305', 3, null, 'XAF', 1000, '2020-01-30'),
        ('306', 3, null, 'XAF', 2000, now())
;

INSERT INTO T_BALANCE(account_id, synced, amount, currency)
    VALUES
        (3, '2020-02-01', 100, 'XAF'),
        (4, '2020-02-01', 0, 'XAF')
;
