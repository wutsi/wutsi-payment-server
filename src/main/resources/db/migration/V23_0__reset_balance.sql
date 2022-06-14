DELETE FROM T_BALANCE;
ALTER TABLE T_BALANCE DROP CONSTRAINT positive_balance;

-- Cash IN
INSERT INTO T_BALANCE(account_id, tenant_id, amount, currency)
    SELECT account_id, tenant_id, sum(net), currency FROM t_transaction WHERE type=1 AND status=1 GROUP BY account_id, tenant_id, currency;

-- Transfer/Charge IN
UPDATE T_BALANCE SET amount = amount +
    (
        SELECT COALESCE (sum(net), 0)
        FROM T_TRANSACTION
        WHERE (type=3 OR type=4) AND status=1 AND T_BALANCE.account_id=T_TRANSACTION.recipient_id
    );

INSERT INTO T_BALANCE(account_id, tenant_id, amount, currency)
    SELECT recipient_id, tenant_id, sum(net), currency
        FROM T_TRANSACTION
        WHERE (type=3 OR type=4) AND status=1 AND recipient_id NOT IN (SELECT account_id FROM T_BALANCE)
        GROUP BY recipient_id, tenant_id, currency;


-- Cash OUT
UPDATE T_BALANCE SET amount = amount -
    (
        SELECT COALESCE (sum(amount), 0)
        FROM T_TRANSACTION
        WHERE type=2 AND status=1 AND T_BALANCE.account_id=T_TRANSACTION.account_id
    );

-- Transfer/Charge OUT
UPDATE T_BALANCE SET amount = amount -
    (
        SELECT COALESCE (sum(amount), 0)
        FROM T_TRANSACTION
        WHERE (type=3 OR type=4) AND status=1 AND T_BALANCE.account_id=T_TRANSACTION.account_id
    );

UPDATE T_BALANCE SET amount=0 WHERE amount<0;
ALTER TABLE T_BALANCE ADD CONSTRAINT positive_balance CHECK (amount >= 0);
