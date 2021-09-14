INSERT INTO T_CHARGE(id, merchant_id, customer_id, user_id, application_id, payment_method_token, payment_method_provider, payment_method_type, external_id, amount, currency, status, gateway_transaction_id, error_code, supplier_error_code, description)
    VALUES
        ('100', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 2, '100-0000', null, null, 'Sample charge'),
        ('101', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 2, '101-0000', null, null, 'Sample charge'),
        ('102', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 2, '102-0000', null, null, 'Sample charge'),

        ('200', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 1, '200-0000', null, '200-1111', 'Sample charge'),
        ('201', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 1, '201-0000', null, '201-1111', 'Sample charge')
;

INSERT INTO T_TRANSACTION(reference_id, from_account_id, to_account_id, description, currency, amount)
    VALUES
        ('201', 11, 1, 'yo', 'XAF', 9900),
        ('201', 11, 1, null, 'XAF', 100)
;
