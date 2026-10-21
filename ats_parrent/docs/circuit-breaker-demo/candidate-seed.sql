-- Run manually in candidate_sdb after candidate-service creates the schema.
INSERT INTO candidates (user_id, full_name, status, source, is_duplicate)
VALUES ('cb000000-0000-0000-0000-000000000003', 'Demo Candidate', 'ACTIVE', 'CB Demo', false)
ON CONFLICT (user_id) DO NOTHING;

-- Copy this ID to demo_candidate_id in application-seed.sql.
SELECT id AS candidate_id FROM candidates
WHERE user_id = 'cb000000-0000-0000-0000-000000000003';
