-- Chunk 6: a run can now fail while fetching the PR from GitHub. (V2 is applied, so it is never edited.)
ALTER TABLE review_runs DROP CONSTRAINT review_runs_failed_stage_check;
ALTER TABLE review_runs ADD CONSTRAINT review_runs_failed_stage_check
    CHECK (failed_stage IN ('FETCH', 'PARSING', 'GENERATION', 'VALIDATION', 'PERSISTENCE'));
