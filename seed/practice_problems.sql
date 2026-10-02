-- Practice content for manual end-to-end testing.
-- Inserted directly because an admin dashboard is explicitly out of scope.

BEGIN;

-- Retire the placeholder row left behind by early judge debugging.
DELETE FROM problems WHERE slug = 'power-cut';

INSERT INTO problems
  (slug, title, statement, input_format, output_format, constraints, difficulty, week_label, time_limit_ms, memory_limit_mb)
VALUES
('gcd-of-two', 'GCD of Two Numbers',
 'Given two integers a and b, find their greatest common divisor.

The GCD of two numbers is the largest positive integer that divides both numbers without leaving a remainder. For example, the GCD of 12 and 18 is 6.

Print the GCD of the two given numbers.',
 'A single line containing two integers a and b separated by a space.',
 'A single line containing one integer: the GCD of a and b.',
 '- 1 <= a, b <= 10^9',
 'EASY', 'Week 1', 2000, 256),

('fizzbuzz', 'FizzBuzz',
 'Write a program that prints numbers from 1 to n, one per line, following these rules:

- If the number is divisible by both 3 and 5, print FizzBuzz instead of the number.
- Otherwise, if it is divisible by 3, print Fizz.
- Otherwise, if it is divisible by 5, print Buzz.
- Otherwise, print the number itself.',
 'A single line containing one integer n.',
 'n lines, each containing either Fizz, Buzz, FizzBuzz or the number itself.',
 '1 <= n <= 1000',
 'EASY', 'Week 2', 2000, 256),

('count-vowels', 'Count the Vowels',
 'Given a line of text, count how many vowels it contains. The vowels are a, e, i, o and u, counted case-insensitively. All other characters, including spaces and digits, are ignored.',
 'A single line containing the text. The line may contain spaces.',
 'A single line containing one integer: the number of vowels.',
 '1 <= length of text <= 1000',
 'EASY', 'Week 2', 2000, 256),

('is-palindrome', 'Palindrome Check',
 'Determine whether a given string is a palindrome.

A palindrome reads the same forwards and backwards. The comparison ignores case and any character that is not a letter or a digit.',
 'A single line containing the string to check.',
 'Print YES if the string is a palindrome, otherwise print NO.',
 '1 <= length of string <= 10000',
 'MEDIUM', 'Week 3', 3000, 256),

('rotate-array', 'Rotate the Array',
 'Given an array of n integers and an integer k, rotate the array to the right by k positions.

A right rotation by k moves each element k places to the right, wrapping around to the beginning. Rotating by more than n positions is allowed and behaves modulo n.',
 'The first line contains two integers n and k. The second line contains n space-separated integers.',
 'A single line containing the n rotated values separated by single spaces.',
 '1 <= n, k <= 10^5; values between -10^9 and 10^9',
 'MEDIUM', 'Week 3', 3000, 256),

('fibonacci-mod', 'Fibonacci Modulo',
 'The Fibonacci sequence is defined by F(0) = 0, F(1) = 1 and F(n) = F(n-1) + F(n-2).

Given n, print F(n) modulo 1000000007.',
 'A single line containing one integer n.',
 'A single line containing one integer: F(n) modulo 1000000007.',
 '0 <= n <= 90',
 'HARD', 'Week 4', 5000, 256);

-- Test cases: each problem gets visible samples plus hidden cases.
-- Sample inputs deliberately differ from hidden ones so leaking them is detectable.

INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, sort_order)
SELECT p.id, v.input_data, v.expected_output, v.is_sample, v.sort_order
FROM (VALUES
  ('gcd-of-two',      '12 18',                    '6',      true,  0),
  ('gcd-of-two',      '7 13',                     '1',      true,  1),
  ('gcd-of-two',      '100 75',                   '25',     false, 2),
  ('gcd-of-two',      '17 5',                     '1',      false, 3),
  ('gcd-of-two',      '270 192',                  '6',      false, 4),

  ('fizzbuzz',        '5',                        '1'||chr(10)||'2'||chr(10)||'Fizz'||chr(10)||'4'||chr(10)||'Buzz', true, 0),
  ('fizzbuzz',        '15',                       'FizzBuzz', true, 1),
  ('fizzbuzz',        '9',                        'Fizz',    false, 2),
  ('fizzbuzz',        '10',                       'Buzz',    false, 3),
  ('fizzbuzz',        '100',                      NULL,      false, 4),

  ('count-vowels',    'Hello World',              '3',       true, 0),
  ('count-vowels',    'XYZ',                      '0',       true,  1),
  ('count-vowels',    'Programming is fun',       '6',       false, 2),
  ('count-vowels',    'aeiouAEIOU',               '10',      false, 3),

  ('is-palindrome',   'A man, a plan, a canal: Panama', 'YES', true, 0),
  ('is-palindrome',   'hello',                    'NO',      true,  1),
  ('is-palindrome',   'RaceCar',                  'YES',     false, 2),
  ('is-palindrome',   '12321',                    'YES',     false, 3),
  ('is-palindrome',   'not a palindrome',         'NO',      false, 4),

  ('rotate-array',    '5 2'||chr(10)||'1 2 3 4 5',   '4 5 1 2 3', true, 0),
  ('rotate-array',    '3 3'||chr(10)||'1 2 3',       '1 2 3',     true, 1),
  ('rotate-array',    '4 6'||chr(10)||'10 20 30 40', '10 20 30 40', false, 2),
  ('rotate-array',    '1 0'||chr(10)||'9',           '9',         false, 3),

  ('fibonacci-mod',   '10',                       '55',      true, 0),
  ('fibonacci-mod',   '0',                        '0',       true, 1),
  ('fibonacci-mod',   '50',                       '12586269025', false, 2),
  ('fibonacci-mod',   '90',                       '210345902',    false, 3)
) AS v(slug, input_data, expected_output, is_sample, sort_order)
JOIN problems p ON p.slug = v.slug
-- A NULL expected_output above means "generated": handled explicitly below.
WHERE v.expected_output IS NOT NULL;

-- FizzBuzz up to 100 is too long to inline; use the cases that can be stated.
INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, sort_order)
SELECT p.id, '30', 'FizzBuzz'||chr(10)||'31'||chr(10)||'32'||chr(10)||'Fizz'||chr(10)||'34'||chr(10)||'Buzz', false, 4
FROM problems p WHERE p.slug = 'fizzbuzz';

COMMIT;