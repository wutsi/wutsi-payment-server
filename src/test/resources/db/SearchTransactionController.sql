INSERT INTO public.t_transaction(id, type, payment_method_token, amount, currency, description, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code, payment_method_provider, tenant_id, user_id, fees, net, recipient_id, created)
	VALUES
	    ('1', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 1, 100, 900, 11, '2020-01-01'),
	    ('2', 2, 'yyy', 9000, 'XAF', 'sample transaction', 1, 'gw-2', 'fin-2', null, 'ERR-0001', 1, 1, 1, 100, 8900, null, now()),
	    ('11', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 11, 100, 900, 11, now()),
	    ('111', 2, 'yyy', 9000, 'XAF', 'sample transaction', 1, 'gw-2', 'fin-2', null, 'ERR-0001', 1, 111, 1, 100, 8900, null, now())
    ;
