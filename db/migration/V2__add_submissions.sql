-- Migration: Add submissions table and update task_results
-- Run this script to add submission tracking support

-- Create submissions table
CREATE TABLE IF NOT EXISTS submissions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    test_id UUID NOT NULL,
    user_id UUID,
    code TEXT NOT NULL,
    language VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Create index on submissions for faster lookups
CREATE INDEX IF NOT EXISTS idx_submissions_task_id ON submissions(task_id);
CREATE INDEX IF NOT EXISTS idx_submissions_user_id ON submissions(user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_created_at ON submissions(created_at DESC);

-- Add submission_id column to task_results if it doesn't exist
ALTER TABLE task_results 
ADD COLUMN IF NOT EXISTS submission_id UUID;

-- Add unique constraint on submission_id
ALTER TABLE task_results 
ADD CONSTRAINT uk_task_results_submission_id UNIQUE (submission_id);

-- Add foreign key to submissions (optional, for referential integrity)
-- ALTER TABLE task_results 
-- ADD CONSTRAINT fk_task_results_submission 
-- FOREIGN KEY (submission_id) REFERENCES submissions(id);

-- Update existing task_results with random UUIDs for submission_id (if needed)
UPDATE task_results 
SET submission_id = gen_random_uuid() 
WHERE submission_id IS NULL;

-- Make submission_id NOT NULL after populating
ALTER TABLE task_results 
ALTER COLUMN submission_id SET NOT NULL;

-- Create index on submission_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_task_results_submission_id ON task_results(submission_id);

-- Add status enum for submissions if it doesn't exist
DO $$ BEGIN
    CREATE TYPE submission_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
