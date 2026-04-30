# SEMLINK 2.0 Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend SEMLINK from a course semantic-integration prototype into a framework-shaped v2 baseline without breaking the existing CLI/demo flow.

**Architecture:** Keep the current single Maven module and existing `SemanticProject` pipeline intact. Add adapter, schema, versioning, reporting, federation, and NL query extension points as additive Java classes in `com.semlink`; document the larger startup architecture separately.

**Tech Stack:** Java 21, Maven, Apache Jena 6.0.0, JUnit 3-compatible tests, RDF/OWL/SPARQL/SHACL.

---

### Task 1: Framework Extension Tests

**Files:**
- Create: `src/test/java/com/semlink/FrameworkExtensionTest.java`

- [x] **Step 1: Write the failing test**

```java
public void testAdapterRegistryCreatesRegisteredAdapter() {
    AdapterRegistry registry = AdapterRegistry.withDefaults();
    DatabaseAdapter adapter = registry.create(ConnectionConfig.owlFile("u1", "src/main/resources/semantic/ontologies/local/university1/university1.ttl"));
    assertEquals("owl", adapter.getType());
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q test -Dtest=FrameworkExtensionTest
```

Expected: compilation failure because extension classes do not exist.

- [x] **Step 3: Implement minimal framework classes**

Create adapter, schema, versioning, reporting, and NL translation classes under `src/main/java/com/semlink/`.

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q test -Dtest=FrameworkExtensionTest
```

Expected: exit code 0.

### Task 2: CLI Extension

**Files:**
- Modify: `src/main/java/com/semlink/Main.java`
- Modify: `src/main/java/com/semlink/SemanticProject.java`

- [x] **Step 1: Add commands without changing existing commands**

Add:

```text
connect add
connect list
pipeline run
schema diff
nl <question>
report
```

- [x] **Step 2: Verify existing command still compiles**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q -DskipTests compile
```

Expected: exit code 0.

### Task 3: Course Blueprint And Use Case 1 Query

**Files:**
- Create: `docs/semantic-integration/semlink-2-master-blueprint.md`
- Create: `src/main/resources/semantic/queries/analysis/cs_students_by_university.rq`
- Modify: `src/main/java/com/semlink/QueryEngine.java`

- [x] **Step 1: Add the AICTE accreditation SPARQL query**

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

- [x] **Step 2: Register the query in `QueryEngine`**

Add `cs_students_by_university` to `QUERY_RESOURCES`.

- [x] **Step 3: Write the course and pitch blueprint**

Document ER/EER, normalization, OWL/RDF, document, graph, KV, SHACL, demo scripts, pitch deck, roadmap, and competitive positioning.

### Task 4: Version Bump

**Files:**
- Modify: `pom.xml`

- [x] **Step 1: Set artifact version**

Change project version from `1.0-SNAPSHOT` to `2.0.0`.

### Task 5: Final Verification

**Files:**
- All modified files.

- [x] **Step 1: Run full tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q test
```

- [x] **Step 2: Run compile**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q -DskipTests compile
```

- [x] **Step 3: Run representative CLI commands**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="query cs_students_by_university"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="schema diff"
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="report"
```
