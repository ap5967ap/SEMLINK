# SEMLINK

Semantic integration of heterogeneous university databases using OWL, RDF, SPARQL, SHACL, and a central AICTE ontology.

## Run

```bash
mvn -q exec:java -Dexec.args="demo"
```

Useful commands:

```bash
mvn -q exec:java -Dexec.args="query all_students"
mvn -q exec:java -Dexec.args="validate"
```

Generated outputs are written to `target/semantic-output/`.

Documentation is available in `docs/semantic-integration/`.

## Project Structure

```text
.
├── docs
│   └── semantic-integration
│       ├── architecture.md
│       ├── demo-script.md
│       ├── query-catalog.md
│       ├── report.md
│       └── screenshot-checklist.md
├── pom.xml
├── README.md
├── src
│   └── main
│       ├── java
│       │   └── com
│       │       └── semlink
│       │           ├── Main.java
│       │           ├── QueryEngine.java
│       │           ├── SemanticProject.java
│       │           └── SimilarityMatcher.java
│       └── resources
│           └── semantic
│               ├── ontologies
│               │   ├── central
│               │   │   └── aicte.ttl
│               │   ├── local
│               │   │   ├── university1
│               │   │   │   ├── reference
│               │   │   │   │   ├── college_populated.owx
│               │   │   │   │   └── college_schema.ttl
│               │   │   │   └── university1.ttl
│               │   │   ├── university2
│               │   │   │   ├── reference
│               │   │   │   │   └── vasu-updated.ttl
│               │   │   │   └── university2.ttl
│               │   │   ├── university3
│               │   │   │   └── university3.ttl
│               │   │   └── university4
│               │   │       └── university4.ttl
│               │   └── support
│               │       └── invalid-sample.ttl
│               ├── queries
│               │   ├── analysis
│               │   │   ├── course_count_by_college.rq
│               │   │   ├── department_to_college_map.rq
│               │   │   ├── student_count_by_department.rq
│               │   │   └── student_count_by_university.rq
│               │   ├── core
│               │   │   ├── all_students.rq
│               │   │   ├── colleges_by_university.rq
│               │   │   ├── courses_by_college.rq
│               │   │   ├── student_college_resolution.rq
│               │   │   └── students_in_computer_science.rq
│               │   └── identity
│               │       ├── same_as_clusters.rq
│               │       └── same_as_student_details.rq
│               ├── rules
│               │   └── alignment.rules
│               └── shapes
│                   └── aicte-shapes.ttl
└── target
    ├── classes
    │   ├── com
    │   │   └── semlink
    │   │       ├── Main.class
    │   │       ├── QueryEngine.class
    │   │       ├── SemanticProject.class
    │   │       ├── SemanticProject$OntologyResource.class
    │   │       ├── SimilarityMatcher.class
    │   │       ├── SimilarityMatcher$Suggestion.class
    │   │       └── SimilarityMatcher$Term.class
    │   ├── db
    │   │   ├── college1
    │   │   ├── college2
    │   │   ├── college3
    │   │   └── college4
    │   └── semantic
    │       ├── ontologies
    │       │   ├── central
    │       │   │   └── aicte.ttl
    │       │   ├── local
    │       │   │   ├── university1
    │       │   │   │   ├── reference
    │       │   │   │   │   ├── college_populated.owx
    │       │   │   │   │   └── college_schema.ttl
    │       │   │   │   └── university1.ttl
    │       │   │   ├── university2
    │       │   │   │   ├── reference
    │       │   │   │   │   └── vasu-updated.ttl
    │       │   │   │   └── university2.ttl
    │       │   │   ├── university3
    │       │   │   │   └── university3.ttl
    │       │   │   └── university4
    │       │   │       └── university4.ttl
    │       │   └── support
    │       │       └── invalid-sample.ttl
    │       ├── queries
    │       │   ├── analysis
    │       │   │   ├── course_count_by_college.rq
    │       │   │   ├── department_to_college_map.rq
    │       │   │   ├── student_count_by_department.rq
    │       │   │   └── student_count_by_university.rq
    │       │   ├── core
    │       │   │   ├── all_students.rq
    │       │   │   ├── colleges_by_university.rq
    │       │   │   ├── courses_by_college.rq
    │       │   │   ├── student_college_resolution.rq
    │       │   │   └── students_in_computer_science.rq
    │       │   └── identity
    │       │       ├── same_as_clusters.rq
    │       │       └── same_as_student_details.rq
    │       ├── rules
    │       │   └── alignment.rules
    │       └── shapes
    │           └── aicte-shapes.ttl
    ├── generated-sources
    │   └── annotations
    ├── maven-status
    │   └── maven-compiler-plugin
    │       └── compile
    │           └── default-compile
    │               ├── createdFiles.lst
    │               └── inputFiles.lst
    ├── semantic-output
    │   ├── exports
    │   │   ├── aicte.owl
    │   │   ├── university1.owl
    │   │   ├── university2.owl
    │   │   ├── university3.owl
    │   │   └── university4.owl
    │   ├── inferred.ttl
    │   ├── mapping-suggestions.tsv
    │   ├── merged.ttl
    │   ├── query-results
    │   │   ├── all_students.txt
    │   │   ├── colleges_by_university.txt
    │   │   ├── course_count_by_college.txt
    │   │   ├── courses_by_college.txt
    │   │   ├── department_to_college_map.txt
    │   │   ├── same_as_clusters.txt
    │   │   ├── same_as_student_details.txt
    │   │   ├── student_college_resolution.txt
    │   │   ├── student_count_by_department.txt
    │   │   ├── student_count_by_university.txt
    │   │   └── students_in_computer_science.txt
    │   ├── summary.txt
    │   └── validation
    │       ├── invalid-report.ttl
    │       └── valid-report.ttl
    └── test-classes

63 directories, 86 files
```
