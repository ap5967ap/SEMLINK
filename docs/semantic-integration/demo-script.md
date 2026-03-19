# Demo Script

## 1. Compile the project

```bash
mvn -q -DskipTests compile
```

## 2. Run the full semantic demo

```bash
mvn -q exec:java -Dexec.args="demo"
```

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

## 7. Show SHACL validation

- `target/semantic-output/validation/valid-report.ttl` should conform.
- `target/semantic-output/validation/invalid-report.ttl` should not conform.

## 8. Show export artifacts

- `target/semantic-output/exports/aicte.owl`
- `target/semantic-output/exports/university1.owl`
- `target/semantic-output/exports/university2.owl`
- `target/semantic-output/exports/university3.owl`
- `target/semantic-output/exports/university4.owl`
