CREATE TABLE university_master (
    university_id VARCHAR(10) PRIMARY KEY,
    university_name VARCHAR(200) NOT NULL
);

CREATE TABLE college_master (
    college_id VARCHAR(10) PRIMARY KEY,
    college_name VARCHAR(200) NOT NULL,
    university_id VARCHAR(10) NOT NULL,
    city VARCHAR(100),
    FOREIGN KEY (university_id) REFERENCES university_master(university_id)
);

CREATE TABLE student_master (
    student_id VARCHAR(10) PRIMARY KEY,
    student_name VARCHAR(200) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    college_id VARCHAR(10) NOT NULL,
    FOREIGN KEY (college_id) REFERENCES college_master(college_id)
);

CREATE TABLE course_master (
    course_id VARCHAR(10) PRIMARY KEY,
    course_title VARCHAR(200) NOT NULL,
    college_id VARCHAR(10) NOT NULL,
    credits INTEGER,
    FOREIGN KEY (college_id) REFERENCES college_master(college_id)
);
