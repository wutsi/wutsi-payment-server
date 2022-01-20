INSERT INTO public.t_transaction(id, tenant_id, account_id, recipient_id, type, amount, currency, description, status, fees, net, requires_approval, expires)
	VALUES
	    ('100', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, current_timestamp + (5 * interval '1 minute')),
	    ('101', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Pending', 2, 49000, 1000, true, '2010-01-01'),
	    ('102', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Success', 1, 49000, 1000, true, null),
	    ('103', 1, 100, 1, 3, 50000, 'XAF', 'Transfer - Failed', 3, 49000, 1000, false, '2010-01-01'),

	    ('200', 9, 100, 1, 1, 50000, 'XAF', 'Cashin - Pending', 2, 49000, 1000, true, null),

	    ('300', 1, 100, 9, 2, 50000, 'XAF', 'Cashout - Pending', 2, 49000, 1000, true, null)
    ;
