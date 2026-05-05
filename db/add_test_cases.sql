-- Add test cases for Two Sum task (a1b2c3d4-e5f6-7890-abcd-ef1234567890)

-- First, let's check the test_input and test_output tables structure
-- We need to create test cases for the Two Sum problem

-- Test Case 1: Basic case
INSERT INTO test_input (test_id, task_test_id, input_data)
VALUES 
    (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', '{"nums": [2, 7, 11, 15], "target": 9}');

-- Test Case 2: Negative numbers
INSERT INTO test_input (test_id, task_test_id, input_data)
VALUES 
    (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', '{"nums": [3, 2, 4], "target": 6}');

-- Test Case 3: Same element twice
INSERT INTO test_input (test_id, task_test_id, input_data)
VALUES 
    (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', '{"nums": [3, 3], "target": 6}');

-- Test Case 4: Large array
INSERT INTO test_input (test_id, task_test_id, input_data)
VALUES 
    (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', '{"nums": [-1, -2, -3, -4, -5], "target": -8}');

-- Test Case 5: Zero sum
INSERT INTO test_input (test_id, task_test_id, input_data)
VALUES 
    (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', '{"nums": [0, 4, 3, 0], "target": 0}');
