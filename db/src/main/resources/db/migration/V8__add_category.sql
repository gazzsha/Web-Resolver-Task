-- V8: Добавление категории (тематики) задачи для фильтрации каталога.
-- Закрывает декларированное в OpenAPI (TaskInfo.category) поле, которое до этого не материализовалось в БД.

ALTER TABLE test
    ADD COLUMN IF NOT EXISTS category VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_test_category ON test(category);

-- Backfill для seed-задач из V4 и V5.
-- Категории соответствуют классификации LeetCode/Codeforces.
UPDATE test SET category = 'Array'         WHERE test_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'::uuid AND category IS NULL; -- Two Sum
UPDATE test SET category = 'Stack'         WHERE test_id = 'b2c3d4e5-f6a7-8901-bcde-f12345678901'::uuid AND category IS NULL; -- Valid Parentheses
UPDATE test SET category = 'Math'          WHERE test_id = 'c3d4e5f6-a7b8-9012-cdef-123456789012'::uuid AND category IS NULL; -- FizzBuzz
UPDATE test SET category = 'String'        WHERE test_id = 'd4e5f6a7-b8c9-0123-def0-234567890123'::uuid AND category IS NULL; -- Reverse String
UPDATE test SET category = 'Array'         WHERE test_id = 'e5f6a7b8-c9d0-1234-ef01-345678901234'::uuid AND category IS NULL; -- Find Max
UPDATE test SET category = 'LinkedList'    WHERE test_id = 'f6a7b8c9-d0e1-2345-f012-456789012345'::uuid AND category IS NULL; -- Add Two Numbers
UPDATE test SET category = 'SlidingWindow' WHERE test_id = 'a7b8c9d0-e1f2-3456-0123-567890123456'::uuid AND category IS NULL; -- Longest Substring Without Repeating Characters

-- Остальным задачам без явной категории присваиваем общую группу "General",
-- чтобы фильтр всегда отображал значимые значения.
UPDATE test SET category = 'General' WHERE category IS NULL;
