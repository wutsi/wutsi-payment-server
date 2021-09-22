DROP INDEX T_TRANSACTION__reference_id;
CREATE INDEX T_TRANSACTION__reference_id_type ON T_TRANSACTION(reference_id, type);
