ALTER TABLE T_TRANSACTION ADD COLUMN tenant_id BIGINT;
UPDATE T_TRANSACTION SET tenant_id=1;
