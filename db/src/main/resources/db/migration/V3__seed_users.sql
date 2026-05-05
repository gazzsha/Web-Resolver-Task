-- V3: Seed users for local dev / demo. Bcrypt hashes generated via pgcrypto's blowfish (compatible with Spring's BCryptPasswordEncoder).
-- Credentials (dev only — DO NOT use in prod):
--   teacher@diplom.local / Teacher123!
--   student1@diplom.local / Student123!
--   student2@diplom.local / Student123!

INSERT INTO users (id, email, password_hash, role, created_at)
SELECT gen_random_uuid(), 'teacher@diplom.local', crypt('Teacher123!', gen_salt('bf', 10)), 'TEACHER', NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'teacher@diplom.local');

INSERT INTO users (id, email, password_hash, role, created_at)
SELECT gen_random_uuid(), 'student1@diplom.local', crypt('Student123!', gen_salt('bf', 10)), 'STUDENT', NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'student1@diplom.local');

INSERT INTO users (id, email, password_hash, role, created_at)
SELECT gen_random_uuid(), 'student2@diplom.local', crypt('Student123!', gen_salt('bf', 10)), 'STUDENT', NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'student2@diplom.local');
