INSERT INTO T_ACCOUNT(id, type, tenant_id, balance, currency) VALUES(1, 2, 1, 100000, 'XAF');
INSERT INTO T_USER(id) VALUES(1);
INSERT INTO T_USER_ACCOUNT(user_fk, account_fk) VALUES (1, 1);

INSERT INTO T_ACCOUNT(id, type, tenant_id, balance, currency) VALUES(200, 2, 1, 10000, 'XAF');
INSERT INTO T_USER(id) VALUES(200);
INSERT INTO T_USER_ACCOUNT(user_fk, account_fk) VALUES (200, 200);
