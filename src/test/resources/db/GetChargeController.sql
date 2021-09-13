INSERT INTO T_CHARGE(id, merchant_id, customer_id, user_id, application_id, payment_method_token, payment_method_provider, payment_method_type, external_id, amount, currency, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code, description)
    VALUES
        ('1111', 1, 11, 111, 1111, '1111-token', 1, 1, 'urn:order:1111', 10000, 'XAF', 3, '1111-0000', '2222-0000', 1, 'FAILURE', 'Sample charge')
;
