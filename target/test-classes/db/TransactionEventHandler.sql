INSERT INTO public.t_transaction(id, type, status, payment_method_token, amount, currency, description, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code, payment_method_provider, tenant_id, account_id, fees, net, recipient_id)
	VALUES
	    ('100', 1, 2, 'xxx', 1000, 'XAF', 'CASHIN - PENDING', 'gw-100', null, null, null, 1, 1, 1, 100, 900, 11),
	    ('101', 1, 2, 'xxx', 1000, 'XAF', 'CASHIN - PENDING', 'gw-11', null, null, null, 1, 1, 11, 100, 900, 11),
	    ('102', 1, 1, 'xxx', 1000, 'XAF', 'CASHIN - SUCCESSFUL', 'gw-111', 'fin-111', null, null, 1, 111, 1, 100, 900, 11),

	    ('200', 2, 2, 'xxx', 1000, 'XAF', 'CASHOUT - PENDING', 'gw-200', null, null, null, 1, 1, 1, 100, 900, 11),
	    ('201', 2, 3, 'xxx', 1000, 'XAF', 'CASHOUT - FAILED', 'gw-11', null, null, null, 1, 1, 11, 100, 900, 11),
	    ('202', 2, 1, 'xxx', 1000, 'XAF', 'CASHOUT - SUCCESSFUL', 'gw-111', 'fin-111', null, null, 1, 111, 1, 100, 900, 11),

	    ('300', 3, 1, 'xxx', 1000, 'XAF', 'TRANSFER - SUCCESSFUL', 'gw-300', null, null, null, 1, 1, 1, 100, 900, 11),
	    ('301', 3, 3, 'xxx', 1000, 'XAF', 'TRANSFER - FAILED', 'gw-1', null, null, null, 1, 1, 1, 100, 900, 11),

	    ('400', 4, 2, 'xxx', 1000, 'XAF', 'CHARGE - PENDING', 'gw-400', null, null, null, 1, 1, 1, 100, 900, 11),
	    ('401', 4, 3, 'xxx', 1000, 'XAF', 'CHARGE - FAILED', 'gw-11', null, null, null, 1, 1, 11, 100, 900, 11),
	    ('402', 4, 1, 'xxx', 1000, 'XAF', 'CHARGE - SUCCESSFUL', 'gw-111', 'fin-111', null, null, 1, 111, 1, 100, 900, 11)
    ;
