CREATE TABLE transfer (
    id                     uuid PRIMARY KEY,
    type                   text        NOT NULL CHECK (type IN ('INTERNAL', 'EXTERNAL', 'DEPOSIT')),
    source_account_id      uuid        NOT NULL REFERENCES account (id),
    destination_account_id uuid REFERENCES account (id),
    destination_ref        text,
    amount_minor           bigint      NOT NULL CHECK (amount_minor > 0),
    currency               char(3)     NOT NULL,
    reference              text,
    status                 text        NOT NULL CHECK (status IN
        ('INITIATED', 'REVIEW', 'HELD', 'POSTED', 'REJECTED', 'FAILED', 'REVERSED')),
    failure_reason         text,
    initiated_by           text        NOT NULL,
    approved_by            text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT destination_matches_type CHECK (
        (type = 'EXTERNAL' AND destination_ref IS NOT NULL AND destination_account_id IS NULL)
        OR (type <> 'EXTERNAL' AND destination_account_id IS NOT NULL AND destination_ref IS NULL)),
    CONSTRAINT checker_is_not_maker CHECK (approved_by IS NULL OR approved_by <> initiated_by)
);

CREATE INDEX transfer_source_idx ON transfer (source_account_id, created_at DESC);
CREATE INDEX transfer_pair_idx ON transfer (source_account_id, destination_account_id) WHERE status = 'POSTED';
CREATE INDEX transfer_review_idx ON transfer (created_at) WHERE status = 'REVIEW';

CREATE TABLE fraud_decision (
    transfer_id uuid PRIMARY KEY REFERENCES transfer (id),
    decision    text        NOT NULL CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    score       int         NOT NULL,
    reasons     jsonb       NOT NULL,
    degraded    boolean     NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE fraud_denylist (
    kind       text        NOT NULL CHECK (kind IN ('DEVICE', 'ACCOUNT', 'DESTINATION')),
    value      text        NOT NULL,
    reason     text        NOT NULL,
    created_by text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, value)
);
