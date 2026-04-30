INSERT INTO u7_main (uid, official_name) VALUES (7, 'Delhi Technical University');
INSERT INTO u7_colleges (cid, cname, parent_uid) VALUES (701, 'DTU Main Campus', 7);

INSERT INTO u7_departments (did, dname) VALUES (10, 'Computer Science');
INSERT INTO u7_departments (did, dname) VALUES (11, 'Mechanical Engineering');

INSERT INTO u7_students (sid, sname, dept_id, coll_id, gpa) VALUES (7001, 'Arjun Singh', 10, 701, 9.5);
INSERT INTO u7_students (sid, sname, dept_id, coll_id, gpa) VALUES (7002, 'Sanya Malhotra', 10, 701, 9.1);
INSERT INTO u7_students (sid, sname, dept_id, coll_id, gpa) VALUES (7003, 'Kabir Khan', 11, 701, 8.4);
