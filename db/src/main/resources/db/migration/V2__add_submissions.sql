-- V2: Track every solution attempt as a Submission, link task_results to its originating submission.

CREATE TABLE IF NOT EXISTS submissions (
    id          UUID PRIMARY KEY,
    task_id     UUID NOT NULL,
    test_id     UUID NOT NULL,
    user_id     UUID,
    code        TEXT NOT NULL,
    language    VARCHAR(255) NOT NULL,
    status      VARCHAR(255) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_submissions_task_id    ON submissions(task_id);
CREATE INDEX IF NOT EXISTS idx_submissions_user_id    ON submissions(user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_created_at ON submissions(created_at DESC);

ALTER TABLE task_results
    ADD COLUMN IF NOT EXISTS submission_id UUID;

UPDATE task_results
   SET submission_id = gen_random_uuid()
 WHERE submission_id IS NULL;

ALTER TABLE task_results
    ALTER COLUMN submission_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'uq_task_results_submission_id'
           AND conrelid = 'task_results'::regclass
    ) THEN
        ALTER TABLE task_results
            ADD CONSTRAINT uq_task_results_submission_id UNIQUE (submission_id);
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_task_results_submission_id ON task_results(submission_id);
