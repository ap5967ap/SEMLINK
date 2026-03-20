# Query Catalog

## Core Queries

- `all_students`: unified student list with department, college, and university
- `students_in_computer_science`: central filter on AICTE department label
- `colleges_by_university`: affiliation listing
- `courses_by_college`: unified college-course listing
- `student_college_resolution`: resolved student-to-college view after reasoning

## Analysis Queries

- `student_count_by_university`: aggregate student counts by university
- `student_count_by_department`: aggregate student counts by department
- `course_count_by_college`: aggregate course counts by college
- `department_to_college_map`: derived department-college-university map

## Identity Queries

- `same_as_clusters`: raw `owl:sameAs` pairs across ontologies
- `same_as_student_details`: identity pairs with AICTE names and IDs

## How To Run A Single Query

```bash
mvn -q exec:java -Dexec.args="query student_count_by_university"
```
