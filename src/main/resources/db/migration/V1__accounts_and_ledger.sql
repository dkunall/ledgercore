CREATE TABLE account (
    id          uuid PRIMARY KEY,
    owner_id    text        NOT NULL,
    type        text        NOT NULL CHECK (type IN ('SAVINGS', 'CURRENT', 'WALLET', 'SYSTEM')),
    currency    char(3)     NOT NULL,
    status      text        NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX account_owner_idx ON account (owner_id);

-- Projection of the postings, updated in the same transaction. The CHECK is the last line of
-- defence against overdraft if application code ever gets the locking wrong.
CREATE TABLE account_balance (
    account_id     uuid PRIMARY KEY REFERENCES account (id),
    currency       char(3) NOT NULL,
    balance_minor  bigint  NOT NULL DEFAULT 0,
    held_minor     bigint  NOT NULL DEFAULT 0 CHECK (held_minor >= 0),
    allow_negative boolean NOT NULL DEFAULT false,
    last_hash      text    NOT NULL,
    version        bigint  NOT NULL DEFAULT 0,
    CONSTRAINT available_not_negative CHECK (allow_negative OR balance_minor - held_minor >= 0)
);

CREATE TABLE journal_entry (
    id                uuid PRIMARY KEY,
    transfer_id       uuid        NOT NULL,
    type              text        NOT NULL CHECK (type IN ('TRANSFER', 'DEPOSIT', 'CAPTURE', 'REVERSAL')),
    reverses_entry_id uuid REFERENCES journal_entry (id),
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX journal_entry_transfer_idx ON journal_entry (transfer_id);
CREATE UNIQUE INDEX journal_entry_single_reversal ON journal_entry (reverses_entry_id) WHERE reverses_entry_id IS NOT NULL;

CREATE TABLE posting (
    id                  bigserial PRIMARY KEY,
    entry_id            uuid    NOT NULL REFERENCES journal_entry (id),
    account_id          uuid    NOT NULL REFERENCES account (id),
    amount_minor        bigint  NOT NULL CHECK (amount_minor <> 0),
    currency            char(3) NOT NULL,
    balance_after_minor bigint  NOT NULL,
    prev_hash           text    NOT NULL,
    hash                text    NOT NULL
);

CREATE INDEX posting_account_idx ON posting (account_id, id);
CREATE INDEX posting_entry_idx ON posting (entry_id);

CREATE TABLE hold (
    id           uuid PRIMARY KEY,
    transfer_id  uuid        NOT NULL UNIQUE,
    account_id   uuid        NOT NULL REFERENCES account (id),
    amount_minor bigint      NOT NULL CHECK (amount_minor > 0),
    currency     char(3)     NOT NULL,
    status       text        NOT NULL CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    resolved_at  timestamptz
);

CREATE FUNCTION reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER journal_entry_append_only BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER journal_entry_no_truncate BEFORE TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER posting_append_only BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER posting_no_truncate BEFORE TRUNCATE ON posting
    FOR EACH STATEMENT EXECUTE FUNCTION reject_mutation();

-- Runs at commit, after every posting of the entry is in place.
CREATE FUNCTION check_entry_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    total      numeric;
    currencies int;
    lines      int;
BEGIN
    SELECT coalesce(sum(amount_minor), 0), count(DISTINCT currency), count(*)
    INTO total, currencies, lines
    FROM posting
    WHERE entry_id = NEW.entry_id;

    IF lines < 2 OR total <> 0 OR currencies <> 1 THEN
        RAISE EXCEPTION 'journal entry % is unbalanced (lines=%, sum=%, currencies=%)',
            NEW.entry_id, lines, total, currencies USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER posting_entry_balanced AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_entry_balanced();
