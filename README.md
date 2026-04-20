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
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o raw example-college"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o assist example-college"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college manual"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="custom run college-pack src/main/resources/semantic/onboarding/custom-sample/college.owl src/main/resources/semantic/onboarding/custom-sample/mapping-rules.rules"
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
│       │           ├── R2oRawExporter.java
│       │           ├── R2oWorkflow.java
│       │           ├── R2rmlRenderer.java
│       │           ├── SemanticProject.java
│       │           ├── SimilarityMatcher.java
│       │           └── SqlInputParser.java
│       └── resources
│           └── semantic
│               ├── ontologies
│               ├── onboarding
│               │   └── custom-sample
│               │       ├── college.owl
│               │       └── mapping-rules.rules
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

2. Raw RDF Export
   Convert the relational database directly into raw RDF triples using standard table, column, and foreign-key projection rules.

3. Assisted Refinement
   Run the local assistant on the raw triples so it promotes obvious facts into an AICTE-ready RDF view and leaves a review report for anything uncertain.

## Custom OWL Onboarding

Colleges can now provide:

- a raw OWL file containing their local classes, properties, and instances
- a separate Jena rules file describing how that OWL should map into AICTE terms

What the application does with those two inputs:

- loads the college OWL as the local source graph
- loads the mapping rules as additional Jena inference rules
- merges the college graph with the central AICTE ontology
- materializes AICTE-aligned triples through the existing reasoning layer
- runs the standard SPARQL query set and SHACL validation
- writes a self-contained output package for that college under `target/semantic-output/custom/`

Expected input shape:

- `college.owl`: the college's local ontology plus instance data, typically in RDF/XML OWL form
- `mapping-rules.rules`: Jena rule syntax that maps local classes and properties into AICTE classes and properties such as `aicte:Student`, `aicte:name`, `aicte:studiesAt`, and `aicte:belongsToUniversity`

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="custom run college-pack src/main/resources/semantic/onboarding/custom-sample/college.owl src/main/resources/semantic/onboarding/custom-sample/mapping-rules.rules"
```

Example package:

- [college.owl](/home/ap/Downloads/SEMLINK/src/main/resources/semantic/onboarding/custom-sample/college.owl)
- [mapping-rules.rules](/home/ap/Downloads/SEMLINK/src/main/resources/semantic/onboarding/custom-sample/mapping-rules.rules)

Outputs are written to `target/semantic-output/custom/<package-name>/` and include:

- `college-input.ttl`
- `mapping-rules.rules`
- `merged.ttl`
- `inferred.ttl`
- `query-results/`
- `validation/report.ttl`
- `summary.txt`
