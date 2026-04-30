CREATE TABLE u7_main (
    uid INT PRIMARY KEY,
    official_name VARCHAR(100)
);

CREATE TABLE u7_colleges (
    cid INT PRIMARY KEY,
    cname VARCHAR(100),
    parent_uid INT,
    FOREIGN KEY (parent_uid) REFERENCES u7_main(uid)
);

CREATE TABLE u7_departments (
    did INT PRIMARY KEY,
    dname VARCHAR(100)
);

CREATE TABLE u7_students (
    sid INT PRIMARY KEY,
    sname VARCHAR(100),
    dept_id INT,
    coll_id INT,
    gpa DOUBLE,
    FOREIGN KEY (dept_id) REFERENCES u7_departments(did),
    FOREIGN KEY (coll_id) REFERENCES u7_colleges(cid)
);
