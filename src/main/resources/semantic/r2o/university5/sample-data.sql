INSERT INTO university_details (univ_id, univ_name) VALUES (1, 'Western Institute of Technology');

INSERT INTO college_info (college_id, college_name, parent_univ_id) VALUES (101, 'WIT Engineering College', 1);
INSERT INTO college_info (college_id, college_name, parent_univ_id) VALUES (102, 'WIT Sciences College', 1);

INSERT INTO department_data (dept_id, dept_name) VALUES (10, 'Computer Science');
INSERT INTO department_data (dept_id, dept_name) VALUES (20, 'Data Science');
INSERT INTO department_data (dept_id, dept_name) VALUES (30, 'Electronics');

INSERT INTO course_catalog (course_code, course_name) VALUES ('CS101', 'Intro to Programming');
INSERT INTO course_catalog (course_code, course_name) VALUES ('DS201', 'Machine Learning Basics');
INSERT INTO course_catalog (course_code, course_name) VALUES ('EC301', 'Digital Circuits');

INSERT INTO student_registry (student_id, full_name, gpa, dept_id, college_id) VALUES (5001, 'Alice Freeman', 9.85, 10, 101);
INSERT INTO student_registry (student_id, full_name, gpa, dept_id, college_id) VALUES (5002, 'Bob Williams', 8.90, 20, 102);
INSERT INTO student_registry (student_id, full_name, gpa, dept_id, college_id) VALUES (5003, 'Charlie Davis', 7.45, 30, 101);
INSERT INTO student_registry (student_id, full_name, gpa, dept_id, college_id) VALUES (5004, 'Diana Prince', 9.99, 10, 101);
INSERT INTO student_registry (student_id, full_name, gpa, dept_id, college_id) VALUES (5005, 'Evan Wright', 9.50, 20, 102);

INSERT INTO student_enrollments (student_id, course_code) VALUES (5001, 'CS101');
INSERT INTO student_enrollments (student_id, course_code) VALUES (5002, 'DS201');
INSERT INTO student_enrollments (student_id, course_code) VALUES (5003, 'EC301');
INSERT INTO student_enrollments (student_id, course_code) VALUES (5004, 'CS101');
INSERT INTO student_enrollments (student_id, course_code) VALUES (5005, 'DS201');
