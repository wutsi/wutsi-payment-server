INSERT INTO T_PAYMENT_REQUEST(id, account_id, tenant_id, amount, currency, description, invoice_id, created, expires)
    VALUES
        ('REQ-3', 200, 1, 50000, 'XAF', 'This is description', 'INV-200', now(), CURRENT_TIMESTAMP + (5 * interval '1 minute'))
;

INSERT INTO public.t_transaction(id, type, payment_method_token, amount, currency, description, status, gateway_transaction_id, financial_transaction_id, error_code, supplier_error_code, payment_method_provider, tenant_id, account_id, fees, net, recipient_id, created, payment_request_fk)
	VALUES
	    ('1', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 1, 100, 900, 11, '2020-01-01', null),
	    ('2', 2, 'yyy', 9000, 'XAF', 'sample transaction', 1, 'gw-2', 'fin-2', null, 'ERR-0001', 1, 1, 1, 100, 8900, null, now(), null),
	    ('11', 1, 'xxx', 1000, 'XAF', 'sample transaction', 1, 'gw-1', 'fin-1', 'NO_ERROR', 'ERR-0001', 1, 1, 11, 100, 900, 11, now(), null),
	    ('111', 2, 'yyy', 9000, 'XAF', 'sample transaction', 1, 'gw-2', 'fin-2', null, 'ERR-0001', 1, 111, 1, 100, 8900, null, now(), null),
	    ('30', 4, null, 9000, 'XAF', 'sample payment', 3, null, null, 'NOT_ENOUGH_FUNDS', null, 1, 1, 1, 9, 9000, null, now(), 'REQ-3')
    ;
