INSERT INTO T_PAYOUT(id, account_id, payment_method_token, amount, currency, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code)
    VALUES
        ('200', 1, 'xxxx', 77777, 'XAF', 2, '200', '200-xxx', null, null),
        ('201', 1, 'xxxx', 66666, 'XAF', 2, '201', '201-xxx', null, null),

        ('400', 1, 'xxxx', 400, 'XAF', 1, '400', null, null, null),
        ('401', 1, 'xxxx', 400, 'XAF', 1, '401', null, null, null),
        ('402', 1, 'xxxx', 400, 'XAF', 1, '402', null, null, null)
;
