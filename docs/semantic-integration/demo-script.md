# Demo Script

## 1. Compile the project

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q -DskipTests compile
```

## 2. Run the full semantic demo

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="demo"
```

## 2A. Show the new R2O onboarding options

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o assist example-college"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college manual"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college refined"
```

- Explain that `manual` uses the hand-authored R2RML mapping.
- Explain that `assist` generates a draft mapping and review report from the relational schema.
- Explain that `refined` is the human-reviewed version of the generated draft.

## 3. Explain the local ontologies

- University 1 is reused directly from the repo’s existing populated college ontology.
- University 2 is curated from the repo seed ontology and renamed around `Learner`, `Institute`, and `Module`.
- University 3 and University 4 are added to complete the heterogeneous landscape.

## 4. Explain the AICTE ontology

- Show that all central queries use `aicte:Student`, `aicte:College`, `aicte:University`, `aicte:Course`, `aicte:name`, `aicte:id`, `aicte:department`, `aicte:studiesAt`, and `aicte:belongsToUniversity`.

## 5. Show reasoning

- Open `target/semantic-output/inferred.ttl`.
- Point out that University 4 students gain `aicte:studiesAt` even though the local ontology stores only `pursuesProgram` and `programHostedBy`.
- Point out the explicit `owl:sameAs` links in `same_as_clusters.txt`.

## 6. Show unified SPARQL results

- `target/semantic-output/query-results/all_students.txt`
- `target/semantic-output/query-results/students_in_computer_science.txt`
- `target/semantic-output/query-results/colleges_by_university.txt`
- `target/semantic-output/query-results/courses_by_college.txt`
- `target/semantic-output/query-results/student_count_by_university.txt`
- `target/semantic-output/query-results/student_count_by_department.txt`
- `target/semantic-output/query-results/course_count_by_college.txt`
- `target/semantic-output/query-results/department_to_college_map.txt`
- `target/semantic-output/query-results/same_as_student_details.txt`

## 7. Show SHACL validation

- `target/semantic-output/validation/valid-report.ttl` should conform.
- `target/semantic-output/validation/invalid-report.ttl` should not conform.

## 8. Show export artifacts

- `target/semantic-output/exports/aicte.owl`
- `target/semantic-output/exports/university1.owl`
- `target/semantic-output/exports/university2.owl`
- `target/semantic-output/exports/university3.owl`
- `target/semantic-output/exports/university4.owl`

## 9. Show the cleaned folder structure

- `src/main/resources/semantic/ontologies/central/`
- `src/main/resources/semantic/ontologies/local/`
- `src/main/resources/semantic/r2o/example-college/`
- `src/main/resources/semantic/queries/core/`
- `src/main/resources/semantic/queries/analysis/`
- `src/main/resources/semantic/queries/identity/`
- `target/semantic-output/r2o/example-college/assisted/`
- `target/semantic-output/r2o/example-college/generated/`
