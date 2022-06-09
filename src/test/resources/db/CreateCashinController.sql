INSERT INTO T_BALANCE(id, account_id, tenant_id, amount, currency)
VALUES (100, 100, 1, 100000, 'XAF');

INSERT INTO public.t_transaction(id, tenant_id, type, account_id, status, error_code, idempotency_key, amount, currency, description)
	VALUES
	    ('100', 1, 1, 1, 1, null, 'i-100', 50000, 'XAF', 'SUCCESS'),
	    ('200', 1, 1, 1, 2, null, 'i-200', 50000, 'XAF', 'PENDING'),
	    ('300', 1, 1, 1, 3, 'NOT_ENOUGH_FUNDS', 'i-300', 50000, 'XAF', 'FAILED')
;
