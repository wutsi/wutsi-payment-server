INSERT INTO T_BALANCE(account_id, tenant_id, amount, currency)
VALUES
    (1, 1, 100000, 'XAF'),
    (200, 1, 200000, 'XAF');

INSERT INTO T_PAYMENT_REQUEST(id, account_id, tenant_id, amount, currency, description, order_id, created, expires)
    VALUES
        ('100', 200, 1, 50000, 'XAF', 'This is description', 'ORDER-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute')),
        ('101', 200, 1, 50000, 'XAF', 'Expired', null, '2020-01-01', '2020-01-02'),
        ('200', 200, 2, 50000, 'XAF', 'This is description', 'ORDER-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute'))
;
