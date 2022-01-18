INSERT INTO T_BALANCE(id, account_id, tenant_id, amount, currency)
    VALUES
        (100, 100, 1, 100000, 'XAF'),
        (1,   1,   1, 200000, 'XAF')
;

INSERT INTO public.t_transaction(id, tenant_id, account_id, recipient_id, type, amount, currency, description, status, fees, net, requires_approval, expires)
	VALUES
	    ('100', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, current_timestamp + (5 * interval '1 minute')),

	    ('900', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, '2010-01-01'),
	    ('901', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Success', 1, 49000, 1000, true, '2010-01-01'),
	    ('902', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, false, '2010-01-01'),
	    ('903', 1, 100, 1, 1, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, '2010-01-01'),

	    ('990', 9, 100, 1, 1, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, '2010-01-01'),
	    ('991', 1, 100, 9, 1, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, '2010-01-01')
    ;
