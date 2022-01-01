INSERT INTO T_BALANCE(account_id, tenant_id, amount, currency)
VALUES
    (1, 1, 100000, 'XAF'),
    (200, 1, 200000, 'XAF'),
    (777, 777, 200000, 'XAF')
;

INSERT INTO T_PAYMENT_REQUEST(id, account_id, tenant_id, amount, currency, description, invoice_id, created, expires)
    VALUES
        ('200', 200, 1, 50000, 'XAF', 'This is description', 'INV-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute')),
        ('201', 200, 1, 5000000, 'XAF', 'This is description', 'INV-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute')),
        ('202', 200, 1, 1000, 'XAF', 'This is description', 'INV-100', '2020-01-01', '2020-01-02'),
        ('200', 1, 1, 50000, 'XAF', 'This is description', 'INV-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute')),
        ('777', 777, 777, 1000, 'XAF', 'This is description', 'INV-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute'))

;
