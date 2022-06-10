ALTER TABLE T_BALANCE DROP CONSTRAINT t_balance_user_id_tenant_id_key;

ALTER TABLE T_BALANCE ADD CONSTRAINT t_balance_account_id_key UNIQUE (account_id);
