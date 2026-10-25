-- Chunk 7: what happened when a run's review was posted to GitHub. (Earlier migrations are applied, so they are never edited.)
ALTER TABLE review_runs ADD COLUMN github_review_id bigint;                              -- null until a review exists on GitHub
ALTER TABLE review_runs ADD COLUMN published_at timestamptz;
ALTER TABLE review_runs ADD COLUMN publish_summary_only boolean NOT NULL DEFAULT false; -- true after the 422 fallback (pitfalls §6)
