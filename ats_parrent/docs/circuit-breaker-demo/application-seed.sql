-- Run manually in application_sdb after schema creation.
-- Replace 42 with the ID returned by candidate-seed.sql.
BEGIN;
INSERT INTO pipeline_stages (stage_name, stage_order, is_default, created_at, updated_at, is_deleted)
SELECT 'CB Demo Applied', 1, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
WHERE NOT EXISTS (SELECT 1 FROM pipeline_stages WHERE stage_name = 'CB Demo Applied');

DO $$
DECLARE demo_candidate_id BIGINT := 42;
BEGIN
    INSERT INTO applications (job_id, candidate_id, cv_url, pipeline_stage_id, status,
                              applied_at, created_at, updated_at, is_deleted)
    SELECT 'cb000000-0000-0000-0000-000000000002', demo_candidate_id, 'https://example.com/cb-demo-cv.pdf',
           (SELECT id FROM pipeline_stages WHERE stage_name = 'CB Demo Applied' ORDER BY id LIMIT 1),
           'SUBMITTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
    WHERE NOT EXISTS (
        SELECT 1 FROM applications
        WHERE job_id = 'cb000000-0000-0000-0000-000000000002' AND candidate_id = demo_candidate_id
    );
END $$;

SELECT id AS application_id, candidate_id FROM applications
WHERE job_id = 'cb000000-0000-0000-0000-000000000002';
COMMIT;
