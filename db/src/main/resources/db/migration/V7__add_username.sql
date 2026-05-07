-- V7: Add username display label to users. Per Boss decision: not unique, not used for login.
-- Existing seed users have no username — wipe and reseed (dev/demo DB only, no prod data).

-- 1. Wipe rows that reference users.id by value (no FK constraints, but kept consistent for dev DB)
DELETE FROM task_results;
DELETE FROM submissions;
DELETE FROM ai_analysis;
DELETE FROM users;

-- 2. Add username column. Length 3-32, latin/digit/underscore.
ALTER TABLE users
    ADD COLUMN username VARCHAR(32) NOT NULL
    CHECK (char_length(username) BETWEEN 3 AND 32 AND username ~ '^[a-zA-Z0-9_]+$');

-- 3. Reseed (matches V3 credentials, just adds username)
INSERT INTO users (id, email, username, password_hash, role, created_at) VALUES
    (gen_random_uuid(), 'teacher@diplom.local',  'teacher',  crypt('Teacher123!', gen_salt('bf', 10)), 'TEACHER', NOW()),
    (gen_random_uuid(), 'student1@diplom.local', 'student1', crypt('Student123!', gen_salt('bf', 10)), 'STUDENT', NOW()),
    (gen_random_uuid(), 'student2@diplom.local', 'student2', crypt('Student123!', gen_salt('bf', 10)), 'STUDENT', NOW());
