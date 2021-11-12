INSERT INTO T_ACCOUNT(id, type, tenant_id, balance, currency, name) VALUES(1, 2, 1, 100000, 'XAF', 'Ray Sponsible');
INSERT INTO T_USER(id) VALUES(1);
INSERT INTO T_USER_ACCOUNT(user_fk, account_fk) VALUES (1, 1)
