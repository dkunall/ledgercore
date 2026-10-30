-- Clearing accounts sit on the other side of money entering (deposits) or leaving (rail payouts)
-- the ledger, so every entry still balances. They may go negative by design.
INSERT INTO account (id, owner_id, type, currency, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'system', 'SYSTEM', 'INR', 'ACTIVE'),
       ('00000000-0000-0000-0000-000000000002', 'system', 'SYSTEM', 'INR', 'ACTIVE');

INSERT INTO account_balance (account_id, currency, allow_negative, last_hash)
VALUES ('00000000-0000-0000-0000-000000000001', 'INR', true, repeat('0', 64)),
       ('00000000-0000-0000-0000-000000000002', 'INR', true, repeat('0', 64));
