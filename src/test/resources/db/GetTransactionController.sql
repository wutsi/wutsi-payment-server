INSERT INTO public.t_transaction(id, type, payment_method_token, amount, currency, description, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code, payment_method_provider, tenant_id, account_id, fees, net, recipient_id)
	VALUES
	    ('1', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 1, 100, 900, 11),
	    ('11', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 11, 11, 900, 11),
	    ('111', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 111, 1, 100, 900, 11),
	    ('5', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 5, 1, 100, 900, 11)
    ;
