INSERT INTO T_ACCOUNT(id, type, tenant_id, balance, currency) VALUES(1, 2, 1, 100000, 'XAF');
INSERT INTO T_USER(id) VALUES(1);
INSERT INTO T_USER_ACCOUNT(user_fk, account_fk) VALUES (1, 1);

INSERT INTO T_ACCOUNT(id, type, tenant_id, balance, currency) VALUES(200, 1, 1, 500000, 'XAF');
INSERT INTO T_GATEWAY(id, code) VALUES(200, 'MTN');
INSERT INTO T_GATEWAY_ACCOUNT(gateway_fk, account_fk) VALUES (200, 200);
