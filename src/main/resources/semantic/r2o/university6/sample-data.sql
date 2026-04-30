-- University 6: Tech Institute of Mumbai
CREATE TABLE university_details (
    id INT PRIMARY KEY,
    name VARCHAR(100)
);

CREATE TABLE college_info (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    uni_id INT,
    FOREIGN KEY (uni_id) REFERENCES university_details(id)
);

CREATE TABLE branch_info (
    branch_id INT PRIMARY KEY,
    branch_name VARCHAR(100)
);

CREATE TABLE student_master (
    roll_no INT PRIMARY KEY,
    full_name VARCHAR(100),
    branch_id INT,
    college_id INT,
    pointer DOUBLE,
    FOREIGN KEY (branch_id) REFERENCES branch_info(branch_id),
    FOREIGN KEY (college_id) REFERENCES college_info(id)
);

INSERT INTO university_details (id, name) VALUES (1, 'Tech Institute of Mumbai');
INSERT INTO college_info (id, name, uni_id) VALUES (101, 'Main Campus Engineering', 1);

INSERT INTO branch_info (branch_id, branch_name) VALUES (1, 'Computer Science');
INSERT INTO branch_info (branch_id, branch_name) VALUES (2, 'Information Technology');

INSERT INTO student_master (roll_no, full_name, branch_id, college_id, pointer) VALUES (6001, 'Rahul Sharma', 1, 101, 9.2);
INSERT INTO student_master (roll_no, full_name, branch_id, college_id, pointer) VALUES (6002, 'Priya Patel', 1, 101, 8.8);
INSERT INTO student_master (roll_no, full_name, branch_id, college_id, pointer) VALUES (6003, 'Amit Shah', 2, 101, 7.5);
