-- V4: Seed 5 algorithmic tasks of various difficulty for demo.
-- Each entry: a row in `test` (problem metadata) + a row in `test_resolve` (signature + test cases).
-- Test cases are stored as JSONB list of {input, expectedOutput} per ru.db.entity.Tests.

-- Task 1: Two Sum (Easy)
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'::uuid,
       'Two Sum',
       'Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target. Read input from stdin: first line — comma-separated nums, second line — target. Print indices separated by space.',
       'Easy'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'::uuid,
       'ArrayInteger',
       '[{"position":0,"type":"ArrayInteger"},{"position":1,"type":"Integer"}]'::jsonb,
       '[{"input":"2,7,11,15\n9","expectedOutput":"0 1"},{"input":"3,2,4\n6","expectedOutput":"1 2"},{"input":"3,3\n6","expectedOutput":"0 1"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'::uuid);

-- Task 2: Valid Parentheses (Easy)
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'b2c3d4e5-f6a7-8901-bcde-f12345678901'::uuid,
       'Valid Parentheses',
       'Given a string s containing just the characters ( ) { } [ ] determine if it is valid. Read s from stdin, print "true" or "false".',
       'Easy'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'b2c3d4e5-f6a7-8901-bcde-f12345678901'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'b2c3d4e5-f6a7-8901-bcde-f12345678901'::uuid,
       'Boolean',
       '[{"position":0,"type":"String"}]'::jsonb,
       '[{"input":"()","expectedOutput":"true"},{"input":"()[]{}","expectedOutput":"true"},{"input":"(]","expectedOutput":"false"},{"input":"([)]","expectedOutput":"false"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'b2c3d4e5-f6a7-8901-bcde-f12345678901'::uuid);

-- Task 3: FizzBuzz (Easy)
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'c3d4e5f6-a7b8-9012-cdef-123456789012'::uuid,
       'FizzBuzz',
       'Read integer n from stdin. Print numbers from 1 to n separated by newline; replace multiples of 3 with Fizz, multiples of 5 with Buzz, multiples of 15 with FizzBuzz.',
       'Easy'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'c3d4e5f6-a7b8-9012-cdef-123456789012'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'c3d4e5f6-a7b8-9012-cdef-123456789012'::uuid,
       'String',
       '[{"position":0,"type":"Integer"}]'::jsonb,
       '[{"input":"5","expectedOutput":"1\n2\nFizz\n4\nBuzz"},{"input":"15","expectedOutput":"1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'c3d4e5f6-a7b8-9012-cdef-123456789012'::uuid);

-- Task 4: Reverse String (Easy)
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'd4e5f6a7-b8c9-0123-def0-234567890123'::uuid,
       'Reverse String',
       'Read a string s from stdin. Print s reversed.',
       'Easy'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'd4e5f6a7-b8c9-0123-def0-234567890123'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'd4e5f6a7-b8c9-0123-def0-234567890123'::uuid,
       'String',
       '[{"position":0,"type":"String"}]'::jsonb,
       '[{"input":"hello","expectedOutput":"olleh"},{"input":"abc","expectedOutput":"cba"},{"input":"a","expectedOutput":"a"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'd4e5f6a7-b8c9-0123-def0-234567890123'::uuid);

-- Task 5: Find Max (Easy)
INSERT INTO test (test_id, title, description, difficulty)
SELECT 'e5f6a7b8-c9d0-1234-ef01-345678901234'::uuid,
       'Find Max',
       'Read a comma-separated list of integers from stdin. Print the maximum value.',
       'Easy'
WHERE NOT EXISTS (SELECT 1 FROM test WHERE test_id = 'e5f6a7b8-c9d0-1234-ef01-345678901234'::uuid);

INSERT INTO test_resolve (problem_id, return_type, arguments, tests)
SELECT 'e5f6a7b8-c9d0-1234-ef01-345678901234'::uuid,
       'Integer',
       '[{"position":0,"type":"ArrayInteger"}]'::jsonb,
       '[{"input":"1,2,3,4,5","expectedOutput":"5"},{"input":"-1,-2,-3","expectedOutput":"-1"},{"input":"42","expectedOutput":"42"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM test_resolve WHERE problem_id = 'e5f6a7b8-c9d0-1234-ef01-345678901234'::uuid);
