# SEMLINK Presentation Flow

This is the exact demo runbook for presenting SEMLINK as an AICTE semantic integration project.

## Setup

Open a terminal at the repository root:

```bash
cd /Users/rutul/Documents/Data_Modelling/SEMLINK
```

Use Java 21 or newer:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q -DskipTests compile
```

Presenter line:

> SEMLINK is a semantic integration framework. It lets AICTE ask one question across universities even when every university stores data differently.

## Phase 1: Problem

Say:

> The real problem is not that data is missing. The problem is that every institution models the same concepts differently: Student, Learner, Pupil, StudentInfo. SEMLINK turns those local models into one AICTE semantic layer.

Point at:

```text
src/main/resources/semantic/ontologies/local/
src/main/resources/semantic/ontologies/central/aicte.ttl
```

## Phase 2: Show The Source Models

Say:

> University1 behaves like a relational system, University2 like a document model, University3 like a graph, and University4 has indirect KV-style relationships. The ontology lets them keep their local structure.

Commands:

```bash
ls src/main/resources/semantic/ontologies/local
ls src/main/resources/semantic/r2o/example-college
ls src/main/resources/semantic/workflows
```

What to explain:

- `aicte.ttl` is the central canonical ontology.
- `alignment.rules` materializes AICTE-aligned facts.
- `aicte-shapes.ttl` defines data quality constraints.
- `queries/` contains the reusable SPARQL catalog.

## Phase 3: Onboarding Flow

### 3.1 Raw relational onboarding

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o raw example-college"
```

Say:

> This step shows SQL schema to raw RDF. Tables, columns, and foreign keys become triples, so the relational model is now visible to the semantic layer.

Point at:

```text
target/semantic-output/r2o/example-college/raw/
```

### 3.2 Assisted alignment

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o assist example-college"
```

Say:

> SEMLINK then promotes obvious raw facts into AICTE-ready RDF and leaves uncertain mappings for review.

Point at:

```text
target/semantic-output/r2o/example-college/assisted/
```

### 3.3 Custom OWL onboarding

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="custom run college-pack src/main/resources/semantic/onboarding/custom-sample/college.owl src/main/resources/semantic/onboarding/custom-sample/mapping-rules.rules"
```

Say:

> If an institution already has OWL, it can submit its ontology and mapping rules directly. SEMLINK runs the same reasoning, validation, and query pipeline.

Point at:

```text
target/semantic-output/custom/college-pack/
```

## Phase 4: Run The Full AICTE Pipeline

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="pipeline run"
```

Say:

> This is the complete SEMLINK flow: merge all universities, infer AICTE facts, validate with SHACL, run every query, and generate a dark presentation report.

Generated outputs:

```text
target/semantic-output/merged.ttl
target/semantic-output/inferred.ttl
target/semantic-output/query-results/
target/semantic-output/validation/
target/semantic-output/index.html
target/semantic-output/summary.txt
```

## Phase 5: Open The Product UI

Start the React + Vite console:

```bash
cd semlink-ui
npm install
npm run dev -- --port 5173
```

Open:

```text
http://127.0.0.1:5173
```

Say:

> This is SEMLINK as a product, not just a command-line project. The console shows the AICTE pipeline from source connection to semantic querying, validation, onboarding, and lineage.

What to click:

1. `Dashboard`: show 4 source models, 24 inferred students, and quality scores.
2. `Connections`: show adapter types for SQL, document, graph, KV, OWL, and CSV.
3. `Query`: click `Run SPARQL` and show the unified Computer Science count with provenance.
4. `Validate`: show SHACL quality by university.
5. `Onboard`: show University5 moving through Connect, Discover, Map, Validate, Publish in 47 seconds.
6. `Lineage`: show source-to-result provenance.
7. `Use Cases`: show the five packaged demo scenarios.

No-server fallback:

```text
target/semantic-output/index.html
```

Say:

> If Wi-Fi or Node setup becomes a distraction, the generated HTML report gives the same AICTE story as a static fallback.

What to point out:

- 4 university source models.
- 12 demo SPARQL queries.
- 24 inferred students.
- SHACL quality gate.
- Per-university compliance status.

## Phase 6: Wow Query

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="query cs_students_by_university"
```

Expected result shape:

```text
| universityName                          | csStudents |
| "Beacon University"                     | 3          |
| "Knowledge Grid University"             | 3          |
| "North State University"                | 3          |
| "Institute of Advanced Tech University" | 2          |
```

Say:

> This is the wow moment: one SPARQL query counts Computer Science students across four different university models.

The query being demonstrated:

```sparql
PREFIX aicte: <https://semlink.example.org/aicte#>

SELECT ?universityName (COUNT(DISTINCT ?student) AS ?csStudents)
WHERE {
  ?student a aicte:Student ;
    aicte:department ?department ;
    aicte:studiesAt ?college .
  ?college aicte:belongsToUniversity ?university .
  ?university aicte:name ?universityName .
  FILTER(LCASE(STR(?department)) = "computer science")
}
GROUP BY ?universityName
ORDER BY DESC(?csStudents) ?universityName
```

## Phase 7: Identity Resolution

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="query same_as_student_details"
```

Say:

> SEMLINK also handles entity identity. If a student appears in two university sources, `owl:sameAs` lets the semantic layer treat them as the same real-world entity.

Point at:

```text
src/main/resources/semantic/queries/identity/same_as_student_details.rq
target/semantic-output/mapping-suggestions.tsv
```

## Phase 8: Data Quality

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="validate"
```

Say:

> OWL tells us what the data means. SHACL tells us whether the data is good enough to trust.

Point at:

```text
src/main/resources/semantic/shapes/aicte-shapes.ttl
target/semantic-output/validation/
```

## Phase 9: Schema Evolution

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="schema diff"
```

Expected result:

```text
Schema diff for aicte: ADDITIVE
Added triples: 1
Removed triples: 0
Version history: .../target/semantic-output/versions.json
```

Say:

> Production systems drift. SEMLINK classifies ontology changes as compatible, additive, or breaking and writes version history for governance.

## Phase 10: Natural Language Query

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="nl Show students with CGPA above 9 from all universities"
```

Say:

> A non-technical registrar can ask a natural-language question. SEMLINK generates transparent SPARQL, validates it, and executes it.

Note:

> The current demo uses deterministic ontology-grounded fallback logic so the presentation is stable without requiring an external LLM.

## Phase 11: Framework Extensibility

Say:

> SEMLINK is not just a university demo. The new adapter framework lets developers add relational, document, graph, key-value, wide-column, OWL, or CSV sources.

Point at:

```text
src/main/java/com/semlink/DatabaseAdapter.java
src/main/java/com/semlink/AdapterRegistry.java
src/main/java/com/semlink/ConnectionConfig.java
src/main/java/com/semlink/SchemaDescriptor.java
src/main/java/com/semlink/MultiSourcePipeline.java
src/main/java/com/semlink/FederatedQueryEngine.java
src/main/java/com/semlink/SemLink.java
```

Optional command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="connect add --type owl --id university1 --path src/main/resources/semantic/ontologies/local/university1/university1.ttl"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="connect list"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="pipeline run --source university1"
```

Point at adapter outputs:

```text
target/semantic-output/pipeline/university1/schema.txt
target/semantic-output/pipeline/university1/raw-export.ttl
target/semantic-output/pipeline/university1/validation-report.ttl
target/semantic-output/pipeline/university1/mapping-review.txt
```

## Phase 11B: Five Scenario Runner

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="usecase list"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="usecase run usecase1"
```

Say:

> The use-case runner packages the before query, after SPARQL, wow moment, data modelling lesson, and runnable command for each demo scenario.

Available scenarios:

- `usecase1`: AICTE accreditation dashboard.
- `usecase2`: student deduplication.
- `usecase3`: regulator data quality report.
- `usecase4`: natural-language query.
- `usecase5`: new institution onboarding.

## Phase 12: Other Workflow Demos

Say:

> AICTE is the course demo. The same pattern applies to e-commerce, healthcare, and fintech.

Open:

```text
src/main/resources/semantic/workflows/aicte-accreditation-flow.md
src/main/resources/semantic/workflows/ecommerce-semantic-flow.md
src/main/resources/semantic/workflows/healthcare-compliance-flow.md
src/main/resources/semantic/workflows/fintech-risk-flow.md
src/main/resources/semantic/workflows/workflow-catalog.json
docs/api/openapi.yaml
docs/ui/semlink-ui-routes.md
semlink-ui/
docs/framework/sdk-example.java
docs/pitch/semlink-demo-day-deck.html
docs/roadmap/github-issues.md
docs/future/future-features.md
docs/competitive/competitive-analysis.md
docs/completion/block-coverage.md
```

Pitch examples:

- E-commerce: user profiles, product catalog, recommendation graph, and events in one semantic query.
- Healthcare: patient records, imaging metadata, genomics, and compliance policy in one semantic query.
- Fintech: transactions, fraud signals, graph proximity, and regulatory reports in one semantic query.

## Closing Script

Say:

> SEMLINK proves that data integration is not only about connecting databases. It is about connecting meaning. With OWL, RDF, SPARQL, SHACL, adapter plugins, and AI-assisted mapping, SEMLINK turns heterogeneous institutional data into one trusted semantic layer.

Final command to show everything still works:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q test
cd semlink-ui && npm test && npm run build
```
