ALTER TABLE T_TRANSACTION DROP COLUMN payment_request_fk;
ALTER TABLE T_TRANSACTION DROP COLUMN retail;
ALTER TABLE T_TRANSACTION DROP COLUMN requires_approval;
ALTER TABLE T_TRANSACTION DROP COLUMN approved;
ALTER TABLE T_TRANSACTION DROP COLUMN fees_to_sender;
ALTER TABLE T_TRANSACTION DROP COLUMN expires;

DROP TABLE T_PAYMENT_REQUEST;
