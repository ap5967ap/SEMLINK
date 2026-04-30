CREATE TABLE university_details (
    univ_id INT PRIMARY KEY,
    univ_name VARCHAR(200)
);

CREATE TABLE college_info (
    college_id INT PRIMARY KEY,
    college_name VARCHAR(200),
    parent_univ_id INT,
    FOREIGN KEY (parent_univ_id) REFERENCES university_details(univ_id)
);

CREATE TABLE department_data (
    dept_id INT PRIMARY KEY,
    dept_name VARCHAR(100)
);

CREATE TABLE course_catalog (
    course_code VARCHAR(20) PRIMARY KEY,
    course_name VARCHAR(100)
);

CREATE TABLE student_registry (
    student_id INT PRIMARY KEY,
    full_name VARCHAR(100),
    gpa DECIMAL(3,2),
    dept_id INT,
    college_id INT,
    FOREIGN KEY (dept_id) REFERENCES department_data(dept_id),
    FOREIGN KEY (college_id) REFERENCES college_info(college_id)
);

CREATE TABLE student_enrollments (
    student_id INT,
    course_code VARCHAR(20),
    FOREIGN KEY (student_id) REFERENCES student_registry(student_id),
    FOREIGN KEY (course_code) REFERENCES course_catalog(course_code)
);
