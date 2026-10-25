-- Chunk 5: every review run and every finding it produced (PLAN §5). Suppressed findings are kept with their reason.
CREATE TABLE review_runs (
    id              uuid PRIMARY KEY,
    request_id      uuid,                       -- set from Chunk 12; dry runs have none
    source          text        NOT NULL CHECK (source IN ('FIXTURE', 'PR', 'DIFF')),
    repo            text        NOT NULL,
    pr_number       integer     NOT NULL,
    base_sha        text        NOT NULL,
    head_sha        text        NOT NULL,
    config          jsonb       NOT NULL,       -- effective ReviewConfig: limits that applied to this run
    provider        text,                       -- null until a model was called ('none' when nothing was reviewable)
    model           text,
    prompt_version  text,
    status          text        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SUPERSEDED')),
    failed_stage    text        CHECK (failed_stage IN ('PARSING', 'GENERATION', 'VALIDATION', 'PERSISTENCE')),
    error_message   text,
    files_reviewed  jsonb       NOT NULL DEFAULT '[]',
    files_skipped   jsonb       NOT NULL DEFAULT '[]',   -- [{path, reason, detail}]
    allowed_locations integer,
    input_tokens    integer,
    output_tokens   integer,
    generation_ms   bigint,
    latency_ms      bigint,
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz
);

CREATE INDEX review_runs_repo_pr_idx ON review_runs (repo, pr_number, started_at DESC);

CREATE TABLE review_findings (
    id                 uuid PRIMARY KEY,
    run_id             uuid        NOT NULL REFERENCES review_runs (id) ON DELETE CASCADE,
    position           integer     NOT NULL,        -- order the model returned the findings in
    location_id        text        NOT NULL,        -- what the model said ("L17"); may be empty or invalid
    file_path          text,                        -- mapped by Java; null when there is no valid location
    line               integer,
    side               text,
    category           text        NOT NULL,        -- the enum name, or the model's raw value when suppressed
    title              text        NOT NULL,
    explanation        text        NOT NULL,
    suggested_fix      text        NOT NULL,
    suggested_test     text        NOT NULL,
    status             text        NOT NULL CHECK (status IN ('PUBLISHABLE', 'SUMMARY_ONLY', 'SUPPRESSED')),
    reason             text,                        -- why it is summary-only or suppressed
    UNIQUE (run_id, position)
);
