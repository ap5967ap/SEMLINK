# SEMLINK

Semantic integration of heterogeneous university databases using OWL, RDF, SPARQL, SHACL, and a central AICTE ontology.

Use JDK 21 or newer. If your shell points to an older Java version, prefix commands with:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

## Run

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="demo"
```

Useful commands:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="query all_students"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="validate"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o assist example-college"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college manual"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college refined"
```

Generated outputs are written to `target/semantic-output/`.

Documentation is available in `docs/semantic-integration/`.

Recommended reading order:

1. `docs/semantic-integration/report.md`
2. `docs/semantic-integration/deep-dive.md`
3. `docs/semantic-integration/r2o-extension.md`
4. `docs/semantic-integration/query-catalog.md`
5. `docs/semantic-integration/real-world-extensions.md`

## Project Structure

```text
.
├── docs
│   └── semantic-integration
│       ├── architecture.md
│       ├── deep-dive.md
│       ├── demo-script.md
│       ├── query-catalog.md
│       ├── r2o-extension.md
│       ├── real-world-extensions.md
│       ├── report.md
│       └── screenshot-checklist.md
├── src
│   └── main
│       ├── java
│       │   └── com
│       │       └── semlink
│       │           ├── Main.java
│       │           ├── QueryEngine.java
│       │           ├── R2oAssistant.java
│       │           ├── R2oWorkflow.java
│       │           ├── R2rmlRenderer.java
│       │           ├── SemanticProject.java
│       │           ├── SimilarityMatcher.java
│       │           └── SqlInputParser.java
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
│               ├── r2o
│               │   └── example-college
│               │       ├── README.md
│               │       ├── generated-aicte-ready.ttl
│               │       ├── r2rml-mapping.ttl
│               │       ├── sample-data.sql
│               │       └── schema.sql
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
├── pom.xml
└── README.md
```

## R2O Modes

The project now supports three Step 0 onboarding modes before the ontology integration flow:

1. Manual R2O
   Use the curated R2RML mapping in `src/main/resources/semantic/r2o/example-college/r2rml-mapping.ttl`.

2. Assisted R2O
   Run the local agentic-style assistant to inspect the relational schema and generate a draft mapping plus review report.

3. Human-Reviewed R2O
   Edit the generated `refined-r2rml-mapping.ttl`, then render RDF from that reviewed mapping.
