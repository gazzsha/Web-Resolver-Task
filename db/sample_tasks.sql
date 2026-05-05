-- Insert sample algorithmic tasks

-- Task 1: Two Sum (Easy)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Two Sum', 
     'Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target. You may assume that each input would have exactly one solution, and you may not use the same element twice.', 
     'Easy');

-- Task 2: Valid Parentheses (Easy)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'Valid Parentheses', 
     'Given a string s containing just the characters ''('', '')'', ''{'', ''}'', ''['' and '']'', determine if the input string is valid. An input string is valid if open brackets are closed by the same type of brackets and in the correct order.', 
     'Easy');

-- Task 3: Merge Two Sorted Lists (Easy)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'Merge Two Sorted Lists', 
     'You are given the heads of two sorted linked lists list1 and list2. Merge the two lists in a one sorted list. The list should be made by splicing together the nodes of the first two lists.', 
     'Easy');

-- Task 4: Best Time to Buy and Sell Stock (Easy)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('d4e5f6a7-b8c9-0123-def0-234567890123', 'Best Time to Buy and Sell Stock', 
     'You are given an array prices where prices[i] is the price of a given stock on the ith day. You want to maximize your profit by choosing a single day to buy one stock and choosing a different day in the future to sell that stock. Return the maximum profit you can achieve.', 
     'Easy');

-- Task 5: Valid Palindrome (Easy)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('e5f6a7b8-c9d0-1234-ef01-345678901234', 'Valid Palindrome', 
     'A phrase is a palindrome if, after converting all uppercase letters into lowercase letters and removing all non-alphanumeric characters, it reads the same forward and backward. Given a string s, return true if it is a palindrome, or false otherwise.', 
     'Easy');

-- Task 6: Add Two Numbers (Medium)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('f6a7b8c9-d0e1-2345-f012-456789012345', 'Add Two Numbers', 
     'You are given two non-empty linked lists representing two non-negative integers. The digits are stored in reverse order, and each of their nodes contains a single digit. Add the two numbers and return the sum as a linked list.', 
     'Medium');

-- Task 7: Longest Substring Without Repeating Characters (Medium)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('a7b8c9d0-e1f2-3456-0123-567890123456', 'Longest Substring Without Repeating Characters', 
     'Given a string s, find the length of the longest substring without repeating characters.', 
     'Medium');

-- Task 8: Container With Most Water (Medium)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('b8c9d0e1-f2a3-4567-1234-678901234567', 'Container With Most Water', 
     'You are given an integer array height of length n. There are n vertical lines drawn such that the two endpoints of the ith line are (i, 0) and (i, height[i]). Find two lines that together with the x-axis form a container, such that the container contains the most water.', 
     'Medium');

-- Task 9: 3Sum (Medium)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('c9d0e1f2-a3b4-5678-2345-789012345678', '3Sum', 
     'Given an integer array nums, return all the triplets [nums[i], nums[j], nums[k]] such that i != j, i != k, and j != k, and nums[i] + nums[j] + nums[k] == 0. Notice that the solution set must not contain duplicate triplets.', 
     'Medium');

-- Task 10: Letter Combinations of a Phone Number (Medium)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('d0e1f2a3-b4c5-6789-3456-890123456789', 'Letter Combinations of a Phone Number', 
     'Given a string containing digits from 2-9 inclusive, return all possible letter combinations that the number could represent. Return the answer in any order.', 
     'Medium');

-- Task 11: Trapping Rain Water (Hard)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('e1f2a3b4-c5d6-7890-4567-901234567890', 'Trapping Rain Water', 
     'Given n non-negative integers representing an elevation map where the width of each bar is 1, compute how much water it can trap after raining.', 
     'Hard');

-- Task 12: Regular Expression Matching (Hard)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('f2a3b4c5-d6e7-8901-5678-012345678901', 'Regular Expression Matching', 
     'Given an input string s and a pattern p, implement regular expression matching with support for ''.'' and ''*'' where ''.'' matches any single character and ''*'' matches zero or more of the preceding element. The matching should cover the entire input string (not partial).', 
     'Hard');

-- Task 13: Merge k Sorted Lists (Hard)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('a3b4c5d6-e7f8-9012-6789-123456789012', 'Merge k Sorted Lists', 
     'You are given an array of k linked-lists lists, each linked-list is sorted in ascending order. Merge all the linked-lists into one sorted linked-list and return it.', 
     'Hard');

-- Task 14: Reverse Nodes in k-Group (Hard)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('b4c5d6e7-f8a9-0123-7890-234567890123', 'Reverse Nodes in k-Group', 
     'Given the head of a linked list, reverse the nodes of the list k at a time, and return the modified list. k is a positive integer and is less than or equal to the length of the linked list. If the number of nodes is not a multiple of k then left-out nodes, in the end, should remain as it is.', 
     'Hard');

-- Task 15: Median of Two Sorted Arrays (Hard)
INSERT INTO test (test_id, title, description, difficulty)
VALUES 
    ('c5d6e7f8-a9b0-1234-8901-345678901234', 'Median of Two Sorted Arrays', 
     'Given two sorted arrays nums1 and nums2 of size m and n respectively, return the median of the two sorted arrays. The overall run time complexity should be O(log (m+n)).', 
     'Hard');
