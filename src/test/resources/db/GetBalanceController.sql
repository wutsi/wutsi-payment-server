INSERT INTO T_TRANSACTION(reference_id, type, account_id, description, currency, amount, created)
    VALUES
        ('101', 1, 1, 'yo', 'XAF', 9900, '2020-01-31'),

        ('201', 1, 1, 'yo', 'XAF', 9900, '2020-02-01'),
        ('201', 2, -100, null, 'XAF', 100,  '2020-02-01'),
        ('202', 1, 1, 'yo', 'XAF', 900,  '2020-02-02'),
        ('202', 2, -100, null, 'XAF', 10,   '2020-02-02'),
        ('203', 1, 1, 'yo', 'XAF', 900,  '2020-02-02'),
        ('203', 2, -100, null, 'XAF', 10,   '2020-02-02'),
        ('204', 1, 1, 'yo', 'XAF', 900,  '2020-02-02'),
        ('204', 2, -100, null, 'XAF', 10,   '2020-02-02'),
        ('205', 1, 2, 'yo', 'XAF', 9900, '2020-02-03'),
        ('205', 2, -100, null, 'XAF', 100,  '2020-02-03'),
        ('204', 3, 1, null, 'XAF', -9900,'2020-02-02')
;


INSERT INTO T_BALANCE(account_id, synced, amount, currency, created)
    VALUES
        (1, '2020-02-01', 10000, 'XAF', '2020-02-02')
;
