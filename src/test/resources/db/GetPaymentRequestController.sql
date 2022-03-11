INSERT INTO T_PAYMENT_REQUEST(id, account_id, tenant_id, amount, currency, description, order_id, created, expires)
    VALUES
        ('200', 200, 1, 50000, 'XAF', 'This is description', 'INV-200', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute')),
        ('777', 777, 777, 1000, 'XAF', 'This is description', 'INV-100', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute'))

;
