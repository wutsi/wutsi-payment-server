INSERT INTO T_CONFIG(id, payment_method_provider, country, fees_percent, fees_value, payout_min_value, payout_max_value)
    VALUES
        (1, 1, 'CM', 0.01, 100, 1000, 1000000)
;

INSERT INTO T_BALANCE(account_id, payment_method_provider, synced, amount, currency, payout_id)
    VALUES
        (100, 1, '2020-02-01', 70000, 'XAF', '100')
;
