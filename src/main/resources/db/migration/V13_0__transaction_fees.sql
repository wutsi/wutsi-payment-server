ALTER TABLE T_TRANSACTION ADD COLUMN fees_to_sender BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE T_TRANSACTION ADD COLUMN business BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE T_TRANSACTION ADD COLUMN retail BOOLEAN NOT NULL DEFAULT false;
