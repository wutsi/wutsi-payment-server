INSERT INTO T_ACCOUNT(id, type, tenant_id, balance, currency) VALUES(100, 2, 1, 100000, 'XAF');
INSERT INTO T_USER(id) VALUES(100);
INSERT INTO T_USER_ACCOUNT(user_fk, account_fk) VALUES (100, 100)
