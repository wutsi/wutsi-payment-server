INSERT INTO public.t_transaction(id, type, payment_method_token, amount, currency, description, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code, payment_method_provider, tenant_id, account_id, fees, net, recipient_id, created, order_id)
	VALUES
	    ('1', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 1, 100, 900, 11, '2020-01-01', null),
	    ('2', 2, 'yyy', 9000, 'XAF', 'sample transaction', 1, 'gw-2', 'fin-2', null, 'ERR-0001', 1, 1, 1, 100, 8900, null, '2020-10-30', null),
	    ('3', 3, 'yyy', 5000, 'XAF', 'sample transaction', 1, null, null, null, null, 1, 1, 10, 100, 8900, 1, now(), null),
	    ('11', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 11, 100, 900, 11, now(), null),
	    ('111', 2, 'yyy', 9000, 'XAF', 'sample transaction', 1, 'gw-2', 'fin-2', null, 'ERR-0001', 1, 111, 1, 100, 8900, null, now(), null),
	    ('30', 3, null, 9000, 'XAF', 'sample payment', 3, null, null, 'NOT_ENOUGH_FUNDS', null, 1, 1, 3, 9, 9000, null, now(), null),
	    ('40', 3, null, 9000, 'XAF', 'sample payment', 3, null, null, 'NOT_ENOUGH_FUNDS', null, 1, 1, 3, 9, 9000, null, now(),  'ORDER-4')
    ;
