INSERT INTO university_master (university_id, university_name) VALUES
('U500', 'Pragati Technical University');

INSERT INTO college_master (college_id, college_name, university_id, city) VALUES
('C501', 'Pragati College of Engineering', 'U500', 'Pune'),
('C502', 'Pragati School of Data Science', 'U500', 'Pune');

INSERT INTO student_master (student_id, student_name, department_name, college_id) VALUES
('S501', 'Rohan Kulkarni', 'Computer Science', 'C501'),
('S502', 'Meera Patil', 'Computer Science', 'C501'),
('S503', 'Aarav Nair', 'Data Science', 'C502');

INSERT INTO course_master (course_id, course_title, college_id, credits) VALUES
('CR701', 'Knowledge Graph Fundamentals', 'C501', 4),
('CR702', 'Applied Data Analytics', 'C502', 4);
