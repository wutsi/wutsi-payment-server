ALTER TABLE T_TRANSACTION ADD COLUMN payment_request_fk VARCHAR(36) REFERENCES T_PAYMENT_REQUEST(id);
