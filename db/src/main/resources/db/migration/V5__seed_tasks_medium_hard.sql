-- V5: Two extra demo tasks — one Medium, one Hard — so the front catalog
-- shows the full difficulty range, not just Easy. Idempotent (NOT EXISTS).

-- Task 6: Add Two Numbers (Medium) — read two integers from stdin (one per line), print their sum.
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'f6a7b8c9-d0e1-2345-f012-456789012345'::uuid,
       'Add Two Numbers',
       'Read two integers a and b from stdin (one per line). Print their sum. Note: input numbers can be negative or zero, and you must handle large integers up to 10^18 (use long in Java).',
       'Medium'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'f6a7b8c9-d0e1-2345-f012-456789012345'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'f6a7b8c9-d0e1-2345-f012-456789012345'::uuid,
       'Integer',
       '[{"position":0,"type":"Integer"},{"position":1,"type":"Integer"}]'::jsonb,
       '[{"input":"2\n3","expectedOutput":"5"},{"input":"-7\n10","expectedOutput":"3"},{"input":"0\n0","expectedOutput":"0"},{"input":"100\n-100","expectedOutput":"0"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'f6a7b8c9-d0e1-2345-f012-456789012345'::uuid);

-- Task 7: Longest Substring Without Repeating Characters (Hard).
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'a7b8c9d0-e1f2-3456-0123-567890123456'::uuid,
       'Longest Substring Without Repeating Characters',
       'Read a string s from stdin. Print the length of the longest substring of s that contains no repeating characters. The string consists of ASCII characters; expected complexity O(n).',
       'Hard'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'a7b8c9d0-e1f2-3456-0123-567890123456'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'a7b8c9d0-e1f2-3456-0123-567890123456'::uuid,
       'Integer',
       '[{"position":0,"type":"String"}]'::jsonb,
       '[{"input":"abcabcbb","expectedOutput":"3"},{"input":"bbbbb","expectedOutput":"1"},{"input":"pwwkew","expectedOutput":"3"},{"input":"","expectedOutput":"0"},{"input":"abcdef","expectedOutput":"6"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'a7b8c9d0-e1f2-3456-0123-567890123456'::uuid);
