-- Chunk 11: one row per webhook delivery GitHub sent us. The delivery id is GitHub's, so a redelivery hits the primary key.
CREATE TABLE webhook_deliveries (
    delivery_id  text        PRIMARY KEY,
    event        text        NOT NULL,
    action       text,
    repo         text,
    pr_number    integer,
    head_sha     text,
    outcome      text        NOT NULL CHECK (outcome IN
        ('ACCEPTED', 'IGNORED_EVENT', 'IGNORED_DRAFT', 'IGNORED_REPO', 'IGNORED_UNTRUSTED', 'SKIPPED_DAILY_CAP')),
    received_at  timestamptz NOT NULL
);
