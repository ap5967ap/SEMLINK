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
