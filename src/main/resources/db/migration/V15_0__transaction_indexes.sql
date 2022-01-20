CREATE INDEX t_transaction_type_status ON T_TRANSACTION(type, status);
CREATE INDEX t_transaction_type_status_expires ON T_TRANSACTION(type, status, expires);
