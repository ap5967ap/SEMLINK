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
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o raw example-college"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o assist example-college"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college manual"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="custom run college-pack src/main/resources/semantic/onboarding/custom-sample/college.owl src/main/resources/semantic/onboarding/custom-sample/mapping-rules.rules"
```

- Explain that `manual` uses the hand-authored R2RML mapping.
- Explain that `raw` exports source-faithful RDF triples directly from the relational schema and rows.
- Explain that `assist` refines those raw triples into an AICTE-ready view and writes a review report.
- Explain that `custom` lets a college provide its own OWL plus mapping rules and run the standard semantic pipeline directly.

For the `custom` path, show:

- the local OWL file as the college-controlled semantic input
- the mapping-rules file as the explicit AICTE projection layer
- `target/semantic-output/custom/college-pack/inferred.ttl` as the integrated result
- `target/semantic-output/custom/college-pack/query-results/all_students.txt` as proof that the normal AICTE queries run unchanged

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
- `src/main/resources/semantic/onboarding/custom-sample/`
- `target/semantic-output/r2o/example-college/raw/`
- `target/semantic-output/r2o/example-college/assisted/`
- `target/semantic-output/r2o/example-college/generated/`
