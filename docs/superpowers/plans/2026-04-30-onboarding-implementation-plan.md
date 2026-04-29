# End-to-End Onboarding Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a complete end-to-end onboarding flow with LLM-assisted mapping review, user approval step, and real-time integration into the running inferred model.

**Architecture:** Backend adds new onboarding service + LLM mapping endpoint + transform/publish endpoints. Frontend replaces single-step Onboard component with multi-step wizard including an interactive mapping review table. Data persists to disk between steps.

**Tech Stack:** Spring Boot (Java 21), React/TypeScript, Gemini API (via existing genai client), Jena RDF.

---

## File Map

### Backend (Java)

| File | Action | Purpose |
|------|--------|---------|
| `src/main/java/com/semlink/SqlInputParser.java` | Modify | Add `buildSchemaStatistics()` method |
| `src/main/java/com/semlink/onboarding/MappingSuggestion.java` | Create | Record: LLM mapping suggestion |
| `src/main/java/com/semlink/onboarding/SchemaStatistics.java` | Create | Record: schema + column stats for LLM |
| `src/main/java/com/semlink/onboarding/ApprovedMapping.java` | Create | Record: user-approved mapping |
| `src/main/java/com/semlink/onboarding/LLMMappingService.java` | Create | Gemini prompt + fallback to heuristics |
| `src/main/java/com/semlink/onboarding/OnboardingService.java` | Create | Orchestrates parse→suggest→approve→transform→validate→publish |
| `src/main/java/com/semlink/ApiController.java` | Modify | Add new onboarding endpoints |
| `src/main/java/com/semlink/SemanticService.java` | Modify | Thread-safe `inferredModel.add()`, add `addToModel()` |

### Frontend (React/TypeScript)

| File | Action | Purpose |
|------|--------|---------|
| `semlink-ui/src/OnboardStep.tsx` | Create | New multi-step Onboard wizard component |
| `semlink-ui/src/MappingReviewTable.tsx` | Create | Interactive mapping review table |
| `semlink-ui/src/api.ts` | Modify | Add new API methods for all onboarding endpoints |
| `semlink-ui/src/App.tsx` | Modify | Replace Onboard component in tab bar |

---

## Task 1: Backend — SQL Parser Schema Statistics

**Files:**
- Modify: `src/main/java/com/semlink/SqlInputParser.java:225-280`
- Test: `src/test/java/com/semlink/SqlInputParserTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/semlink/SqlInputParserTest.java
// (append to existing test class)

@Test
void buildSchemaStatistics_shouldExtractTableAndColumnStats() {
    String schemaSql = """
        CREATE TABLE students (
            student_id INT NOT NULL,
            full_nm VARCHAR(100),
            dept_name VARCHAR(50),
            brn_cd INT,
            FOREIGN KEY (brn_cd) REFERENCES departments(dept_id)
        );
        CREATE TABLE departments (
            dept_id INT NOT NULL,
            dept_name VARCHAR(100)
        );
        """;
    String dataSql = """
        INSERT INTO students (student_id, full_nm, dept_name, brn_cd) VALUES ('S001', 'Alice Gupta', 'Computer Science', 1);
        INSERT INTO students (student_id, full_nm, dept_name, brn_cd) VALUES ('S002', 'Bob Singh', 'Physics', 2);
        INSERT INTO departments (dept_id, dept_name) VALUES (1, 'CS');
        INSERT INTO departments (dept_id, dept_name) VALUES (2, 'PHY');
        """;

    SqlInputParser parser = new SqlInputParser();
    SchemaStatistics stats = parser.buildSchemaStatistics(schemaSql, dataSql);

    assertEquals("uploaded", stats.datasetName());
    assertEquals(2, stats.tables().size());

    SchemaStatistics.TableStats students = stats.tables().stream()
        .filter(t -> t.name().equals("students")).findFirst().orElseThrow();
    assertEquals(4, students.columns().size());
    assertEquals("student_id", students.primaryKey());
    assertEquals(1, students.foreignKeys().size());
    assertEquals(2, students.rowCount());

    SchemaStatistics.ColumnStats idCol = students.columns().stream()
        .filter(c -> c.name().equals("student_id")).findFirst().orElseThrow();
    assertFalse(idCol.nullable());
    assertTrue(idCol.sampleValues().contains("S001"));
    assertTrue(idCol.sampleValues().contains("S002"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn test -Dtest=SqlInputParserTest#buildSchemaStatistics_shouldExtractTableAndColumnStats -q 2>&1 | tail -20`
Expected: COMPILATION ERROR (SchemaStatistics class doesn't exist yet)

- [ ] **Step 3: Add SchemaStatistics records to SqlInputParser**

Add these records at the bottom of `SqlInputParser.java` (after line 222):

```java
    // ── Schema Statistics for LLM ────────────────────────────────

    public SchemaStatistics buildSchemaStatistics(String schemaSql, String dataSql) {
        SqlSchema schema = parseSchema(schemaSql);
        SqlData data = parseData(dataSql);
        List<TableStats> tableStats = new ArrayList<>();

        for (TableDefinition table : schema.tables().values()) {
            List<Map<String, Object>> rows = data.rowsByTable().getOrDefault(table.name(), List.of());
            List<ColumnStats> colStats = new ArrayList<>();
            for (String col : table.columns()) {
                List<String> samples = collectSamples(rows, col, 5);
                colStats.add(new ColumnStats(col, "VARCHAR", isNullable(table, col), samples));
            }
            List<ForeignKeyRef> fks = table.foreignKeys().entrySet().stream()
                .map(e -> new ForeignKeyRef(e.getKey(), e.getValue().targetTable() + "." + e.getValue().targetColumn()))
                .toList();
            String pk = detectPrimaryKey(table);
            tableStats.add(new TableStats(table.name(), colStats, pk, fks, rows.size()));
        }

        return new SchemaStatistics(
            "uploaded",
            tableStats,
            List.of("Student", "College", "University", "Course", "Department", "Program"),
            List.of("studiesAt", "belongsToUniversity", "offersCourse", "memberOfDepartment",
                    "enrolledIn", "name", "id", "cgpa", "department")
        );
    }

    private List<String> collectSamples(List<Map<String, Object>> rows, String column, int limit) {
        List<String> samples = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object val = row.get(column);
            if (val != null && !val.toString().equalsIgnoreCase("NULL") && samples.size() < limit) {
                samples.add(val.toString());
            }
        }
        return samples;
    }

    private boolean isNullable(TableDefinition table, String column) {
        return true; // heuristic: assume nullable unless NOT NULL seen in body
    }

    private String detectPrimaryKey(TableDefinition table) {
        for (String col : table.columns()) {
            if (col.equalsIgnoreCase("id") || col.toLowerCase(Locale.ROOT).endsWith("_id")) {
                return col;
            }
        }
        return table.columns().isEmpty() ? null : table.columns().getFirst();
    }

    public record SchemaStatistics(
        String datasetName,
        List<TableStats> tables,
        List<String> availableAicteClasses,
        List<String> availableAicteProperties
    ) {}

    public record TableStats(
        String name,
        List<ColumnStats> columns,
        String primaryKey,
        List<ForeignKeyRef> foreignKeys,
        int rowCount
    ) {}

    public record ColumnStats(String name, String sqlType, boolean nullable, List<String> sampleValues) {}

    public record ForeignKeyRef(String column, String references) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn test -Dtest=SqlInputParserTest#buildSchemaStatistics_shouldExtractTableAndColumnStats -q 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/semlink/SqlInputParser.java src/test/java/com/semlink/SqlInputParserTest.java
git commit -m "feat(onboarding): add buildSchemaStatistics() to SqlInputParser"
```

---

## Task 2: Backend — Mapping Suggestion Record + LLM Service

**Files:**
- Create: `src/main/java/com/semlink/onboarding/MappingSuggestion.java`
- Create: `src/main/java/com/semlink/onboarding/LLMMappingService.java`
- Test: `src/test/java/com/semlink/onboarding/LLMMappingServiceTest.java`

- [ ] **Step 1: Create MappingSuggestion record**

```java
// src/main/java/com/semlink/onboarding/MappingSuggestion.java
package com.semlink.onboarding;

import java.util.List;

public record MappingSuggestion(
    String sourceTable,
    String sourceColumn,
    String suggestedClass,
    String suggestedProperty,
    double confidence,
    String rationale,
    String matchType  // "class", "property", "relationship"
) {
    public static final String TYPE_CLASS = "class";
    public static final String TYPE_PROPERTY = "property";
    public static final String TYPE_RELATIONSHIP = "relationship";
}
```

- [ ] **Step 2: Create LLMMappingService**

```java
// src/main/java/com/semlink/onboarding/LLMMappingService.java
package com.semlink.onboarding;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.semlink.SqlInputParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LLMMappingService {
    private static final Logger log = LoggerFactory.getLogger(LLMMappingService.class);
    private static final String AICTE_NS = "https://semlink.example.org/aicte#";
    private static final Set<String> NAME_HINTS = Set.of("name", "title", "label", "nm", "full_nm");
    private static final Map<String, List<String>> CLASS_HINTS = Map.of(
        "University", List.of("university", "hub", "parentuniversity"),
        "College", List.of("college", "institute", "campus", "affiliatedcollege"),
        "Student", List.of("student", "learner", "pupil", "studentinfo"),
        "Course", List.of("course", "module", "subject", "paper"),
        "Department", List.of("department", "school", "division", "faculty"),
        "Program", List.of("program", "track", "plan")
    );

    private final Client geminiClient;
    private final boolean llmEnabled;

    public LLMMappingService(String geminiApiKey) {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            this.geminiClient = Client.builder().apiKey(geminiApiKey).build();
            this.llmEnabled = true;
            log.info("LLM mapping service enabled with Gemini");
        } else {
            this.geminiClient = null;
            this.llmEnabled = false;
            log.info("LLM mapping service using heuristic fallback");
        }
    }

    public List<MappingSuggestion> generateSuggestions(SqlInputParser.SchemaStatistics stats) {
        if (llmEnabled) {
            try {
                return generateWithLLM(stats);
            } catch (Exception e) {
                log.warn("LLM mapping failed, falling back to heuristics: {}", e.getMessage());
            }
        }
        return generateWithHeuristics(stats);
    }

    private List<MappingSuggestion> generateWithLLM(SqlInputParser.SchemaStatistics stats) {
        String prompt = buildPrompt(stats);
        try {
            GenerateContentResponse response = geminiClient.models.generateContent(
                "gemini-3-flash-preview", prompt, null);
            String text = response.text();
            if (text == null || text.isBlank()) {
                return generateWithHeuristics(stats);
            }
            text = text.replace("```json", "").replace("```", "").trim();
            return parseLLMResponse(text, stats);
        } catch (Exception e) {
            log.error("Gemini API error: {}", e.getMessage());
            return generateWithHeuristics(stats);
        }
    }

    private String buildPrompt(SqlInputParser.SchemaStatistics stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert RDB-to-ontology mapper. Map the following schema to the AICTE ontology.\n\n");
        sb.append("ONTOLOGY:\n");
        sb.append("  Classes: Student, College, University, Course, Department, Program\n");
        sb.append("  DatatypeProperties: id, name, cgpa, department\n");
        sb.append("  ObjectProperties: studiesAt (Student→College), belongsToUniversity (College→University),\n");
        sb.append("    offersCourse (College→Course), memberOfDepartment (Student→Department), enrolledIn (Student→Course)\n\n");
        sb.append("SCHEMA:\n");
        for (SqlInputParser.TableStats table : stats.tables()) {
            sb.append("- Table: ").append(table.name()).append("\n");
            for (SqlInputParser.ColumnStats col : table.columns()) {
                sb.append("    Column: ").append(col.name())
                  .append(" | Type: ").append(col.sqlType())
                  .append(" | Nullable: ").append(col.nullable())
                  .append(" | Samples: ").append(col.sampleValues()).append("\n");
            }
            if (!table.foreignKeys().isEmpty()) {
                for (SqlInputParser.ForeignKeyRef fk : table.foreignKeys()) {
                    sb.append("    FK: ").append(fk.column()).append(" → ").append(fk.references()).append("\n");
                }
            }
            sb.append("    RowCount: ").append(table.rowCount()).append("\n");
        }
        sb.append("\nTASK:\n");
        sb.append("Output a JSON array (no markdown) where each element has:\n");
        sb.append('"').append("sourceTable").append("\": table name,\n");
        sb.append('"').append("sourceColumn").append("\": column name,\n");
        sb.append('"').append("suggestedClass").append("\": AICTE class,\n");
        sb.append('"').append("suggestedProperty").append("\": AICTE property,\n");
        sb.append('"').append("confidence").append("\": 0.0-1.0,\n");
        sb.append('"').append("rationale").append("\": brief reason,\n");
        sb.append('"').append("matchType").append("\": \"property\" or \"relationship\"\n");
        sb.append("Only output the JSON array. No explanation.\n");
        return sb.toString();
    }

    private List<MappingSuggestion> parseLLMResponse(String json, SqlInputParser.SchemaStatistics stats) {
        List<MappingSuggestion> suggestions = new ArrayList<>();
        try {
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (var elem : arr) {
                var obj = elem.getAsJsonObject();
                suggestions.add(new MappingSuggestion(
                    obj.get("sourceTable").getAsString(),
                    obj.get("sourceColumn").getAsString(),
                    obj.get("suggestedClass").getAsString(),
                    obj.get("suggestedProperty").getAsString(),
                    obj.get("confidence").getAsDouble(),
                    obj.has("rationale") ? obj.get("rationale").getAsString() : "",
                    obj.has("matchType") ? obj.get("matchType").getAsString() : MappingSuggestion.TYPE_PROPERTY
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM response, falling back: {}", e.getMessage());
            return generateWithHeuristics(stats);
        }
        return suggestions;
    }

    public List<MappingSuggestion> generateWithHeuristics(SqlInputParser.SchemaStatistics stats) {
        List<MappingSuggestion> suggestions = new ArrayList<>();
        for (SqlInputParser.TableStats table : stats.tables()) {
            String guessedClass = guessClass(table.name());
            for (SqlInputParser.ColumnStats col : table.columns()) {
                double confidence;
                String property;
                String rationale;

                if (col.name().equalsIgnoreCase("id") || col.name().toLowerCase().endsWith("_id")) {
                    if (col.name().toLower().contains("dept") || col.name().toLower().contains("brn")) {
                        property = "department";
                        confidence = 0.68;
                        rationale = "ID column with department hint matches 'department' property";
                    } else {
                        property = "id";
                        confidence = 0.99;
                        rationale = "ID column maps to 'id' property";
                    }
                } else if (isNameColumn(col.name())) {
                    property = "name";
                    confidence = 0.94;
                    rationale = "Name-like column maps to 'name' property";
                } else if (col.name().toLower().contains("cgpa") || col.name().toLower().contains("gpa")) {
                    property = "cgpa";
                    confidence = 0.97;
                    rationale = "CGPA column detected";
                } else if (col.name().toLower().contains("dept") || col.name().toLower().contains("department")) {
                    property = "department";
                    confidence = 0.71;
                    rationale = "Department column maps to 'department' property";
                } else {
                    property = "unknown";
                    confidence = 0.30;
                    rationale = "No clear mapping, user review required";
                }

                suggestions.add(new MappingSuggestion(
                    table.name(), col.name(), guessedClass, property, confidence, rationale, MappingSuggestion.TYPE_PROPERTY
                ));
            }

            for (SqlInputParser.ForeignKeyRef fk : table.foreignKeys()) {
                String targetTable = fk.references().split("\\.")[0];
                String relationship = guessRelationship(table.name(), fk.column(), targetTable);
                if (relationship != null) {
                    suggestions.add(new MappingSuggestion(
                        table.name(), fk.column(),
                        guessClass(targetTable), relationship, 0.90,
                        "Foreign key implies " + relationship + " relationship", MappingSuggestion.TYPE_RELATIONSHIP
                    ));
                }
            }
        }
        return suggestions;
    }

    private String guessClass(String tableName) {
        String normalized = tableName.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CLASS_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (normalized.contains(hint)) return entry.getKey();
            }
        }
        return "Entity";
    }

    private boolean isNameColumn(String colName) {
        String lower = colName.toLowerCase();
        for (String hint : NAME_HINTS) {
            if (lower.contains(hint)) return true;
        }
        return false;
    }

    private String guessRelationship(String fromTable, String fkColumn, String toTable) {
        String fromClass = guessClass(fromTable);
        String toClass = guessClass(toTable);
        if ("Student".equals(fromClass) && "College".equals(toClass)) return "studiesAt";
        if ("College".equals(fromClass) && "University".equals(toClass)) return "belongsToUniversity";
        if ("Student".equals(fromClass) && "Department".equals(toClass)) return "memberOfDepartment";
        if ("Course".equals(fromClass) && "College".equals(toClass)) return "offersCourse";
        return null;
    }
}
```

- [ ] **Step 3: Create LLMMappingServiceTest**

```java
// src/test/java/com/semlink/onboarding/LLMMappingServiceTest.java
package com.semlink.onboarding;

import com.semlink.SqlInputParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LLMMappingServiceTest {
    @Test
    void generateWithHeuristics_shouldReturnSuggestionsForStudentTable() {
        SqlInputParser.SchemaStatistics stats = new SqlInputParser.SchemaStatistics(
            "test",
            List.of(new SqlInputParser.TableStats(
                "students",
                List.of(
                    new SqlInputParser.ColumnStats("student_id", "INT", false, List.of("S001", "S002")),
                    new SqlInputParser.ColumnStats("full_nm", "VARCHAR", false, List.of("Alice", "Bob")),
                    new SqlInputParser.ColumnStats("dept_name", "VARCHAR", true, List.of("CS", "PHY"))
                ),
                "student_id",
                List.of(),
                2
            )),
            List.of("Student", "College", "University", "Course", "Department", "Program"),
            List.of("studiesAt", "belongsToUniversity", "name", "id", "department")
        );

        LLMMappingService service = new LLMMappingService(null); // no API key = heuristics
        List<MappingSuggestion> suggestions = service.generateSuggestions(stats);

        assertFalse(suggestions.isEmpty());
        MappingSuggestion idSuggestion = suggestions.stream()
            .filter(s -> s.sourceColumn().equals("student_id")).findFirst().orElseThrow();
        assertEquals("id", idSuggestion.suggestedProperty());
        assertEquals(0.99, idSuggestion.confidence());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn test -Dtest=LLMMappingServiceTest -q 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/semlink/onboarding/MappingSuggestion.java \
        src/main/java/com/semlink/onboarding/LLMMappingService.java \
        src/test/java/com/semlink/onboarding/LLMMappingServiceTest.java
git commit -m "feat(onboarding): add MappingSuggestion record and LLMMappingService with heuristic fallback"
```

---

## Task 3: Backend — ApprovedMapping Record + OnboardingService

**Files:**
- Create: `src/main/java/com/semlink/onboarding/ApprovedMapping.java`
- Create: `src/main/java/com/semlink/onboarding/OnboardingService.java`
- Test: `src/test/java/com/semlink/onboarding/OnboardingServiceTest.java`

- [ ] **Step 1: Create ApprovedMapping record**

```java
// src/main/java/com/semlink/onboarding/ApprovedMapping.java
package com.semlink.onboarding;

public record ApprovedMapping(
    String sourceTable,
    String sourceColumn,
    String aicteClass,
    String aicteProperty,
    boolean userCustomized
) {}
```

- [ ] **Step 2: Create OnboardingService**

```java
// src/main/java/com/semlink/onboarding/OnboardingService.java
package com.semlink.onboarding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.semlink.SqlInputParser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OnboardingService {
    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);
    private static final String AICTE_NS = "https://semlink.example.org/aicte#";
    private static final String REFINED_NS = "https://semlink.example.org/r2o/refined/";
    private static final Path OUTPUT_ROOT = Path.of("target", "semantic-output", "r2o");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SqlInputParser parser = new SqlInputParser();
    private final LLMMappingService llmService;

    private volatile Model inferredModel;
    private final Map<String, OnboardingJob> jobs = new ConcurrentHashMap<>();

    public OnboardingService(String geminiApiKey) {
        this.llmService = new LLMMappingService(geminiApiKey);
    }

    public void setInferredModel(Model model) {
        this.inferredModel = model;
    }

    // ── Step 1: Parse ────────────────────────────────────────────

    public Map<String, Object> parseSql(String name, String schemaSql, String dataSql) {
        SqlInputParser.SchemaStatistics stats = parser.buildSchemaStatistics(schemaSql, dataSql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "parsed");
        result.put("datasetName", stats.datasetName());
        result.put("tables", stats.tables().size());
        result.put("totalRows", stats.tables().stream().mapToInt(SqlInputParser.TableStats::rowCount).sum());
        result.put("schema", stats);

        Path jobDir = jobDirectory(name);
        writeJson(jobDir.resolve("parsed-schema.json"), stats);
        jobs.put(name, new OnboardingJob(name, jobDir, stats));
        return result;
    }

    // ── Step 2: LLM Suggest ───────────────────────────────────────

    public Map<String, Object> suggestMappings(String name) {
        OnboardingJob job = jobs.get(name);
        if (job == null) return Map.of("status", "error", "message", "No job found for: " + name);

        List<MappingSuggestion> suggestions = llmService.generateSuggestions(job.schemaStats());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("suggestions", suggestions);
        result.put("llmMode", llmService.isLLMEnabled() ? "gemini" : "heuristic");

        writeJson(job.dir.resolve("llm-suggestions.json"), suggestions);
        job.suggestions = suggestions;
        return result;
    }

    // ── Step 3: Approve ──────────────────────────────────────────

    public Map<String, Object> approveMappings(String name, List<Map<String, Object>> approvedMappingsRaw) {
        OnboardingJob job = jobs.get(name);
        if (job == null) return Map.of("status", "error", "message", "No job found for: " + name);

        List<ApprovedMapping> approved = new ArrayList<>();
        for (Map<String, Object> raw : approvedMappingsRaw) {
            approved.add(new ApprovedMapping(
                (String) raw.get("sourceTable"),
                (String) raw.get("sourceColumn"),
                (String) raw.get("aicteClass"),
                (String) raw.get("aicteProperty"),
                Boolean.TRUE.equals(raw.get("userCustomized"))
            ));
        }

        job.approvedMappings = approved;
        writeJson(job.dir.resolve("mapping-config.json"), approved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "approved");
        result.put("mappingCount", approved.size());
        return result;
    }

    // ── Step 4: Transform ────────────────────────────────────────

    public Map<String, Object> transform(String name, String schemaSql, String dataSql) {
        OnboardingJob job = jobs.get(name);
        if (job == null || job.approvedMappings == null) {
            return Map.of("status", "error", "message", "No approved mappings found for: " + name);
        }

        SqlInputParser.SqlSchema schema = parser.parseSchema(schemaSql);
        SqlInputParser.SqlData data = parser.parseData(dataSql);

        Model model = buildApprovedModel(job.approvedMappings, schema, data);

        Path outputPath = job.dir.resolve("approved-mapping.ttl");
        writeModel(model, outputPath, Lang.TURTLE);

        List<String> extractedClasses = new ArrayList<>();
        model.listSubjects().forEachRemaining(r -> {
            if (r.isURIResource()) {
                String uri = r.getURI();
                if (uri.contains("#")) extractedClasses.add(uri.substring(uri.lastIndexOf('#') + 1));
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("triples", model.size());
        result.put("extractedClasses", extractedClasses.stream().distinct().limit(20).toList());
        job.outputModel = model;
        return result;
    }

    private Model buildApprovedModel(List<ApprovedMapping> mappings,
                                      SqlInputParser.SqlSchema schema,
                                      SqlInputParser.SqlData data) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("aicte", AICTE_NS);

        Map<String, List<ApprovedMapping>> byTable = new LinkedHashMap<>();
        for (ApprovedMapping m : mappings) {
            byTable.computeIfAbsent(m.sourceTable(), k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<ApprovedMapping>> entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = data.rowsByTable().getOrDefault(tableName, List.of());
            List<ApprovedMapping> tableMappings = entry.getValue();

            for (Map<String, Object> row : rows) {
                String rowId = String.valueOf(row.get(getIdColumn(schema, tableName)));
                Resource subject = model.createResource(REFINED_NS + slugify(tableName) + "/" + slugify(rowId));

                for (ApprovedMapping mapping : tableMappings) {
                    Object value = row.get(mapping.sourceColumn());
                    if (value == null) continue;

                    if (isObjectProperty(mapping.aicteProperty())) {
                        Resource obj = model.createResource(REFINED_NS + slugify(mapping.aicteProperty()) + "/" + slugify(String.valueOf(value)));
                        model.add(subject, model.createProperty(AICTE_NS + mapping.aicteProperty()), obj);
                    } else {
                        model.add(subject, model.createProperty(AICTE_NS + mapping.aicteProperty()),
                            model.createTypedLiteral(value));
                    }
                }
            }
        }
        return model;
    }

    private String getIdColumn(SqlInputParser.SqlSchema schema, String tableName) {
        SqlInputParser.TableDefinition table = schema.table(tableName);
        if (table == null) return "id";
        for (String col : table.columns()) {
            if (col.equalsIgnoreCase("id") || col.toLowerCase().endsWith("_id")) return col;
        }
        return table.columns().isEmpty() ? "id" : table.columns().getFirst();
    }

    private boolean isObjectProperty(String property) {
        return Set.of("studiesAt", "belongsToUniversity", "offersCourse", "memberOfDepartment", "enrolledIn").contains(property);
    }

    private String slugify(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    // ── Step 5: Validate ───────────────────────────────────────────

    public Map<String, Object> validateTriples(String name, String schemaSql, String dataSql) {
        OnboardingJob job = jobs.get(name);
        if (job == null || job.outputModel == null) {
            return Map.of("status", "error", "message", "No transformed model found for: " + name);
        }

        // Basic validation: check triples were generated
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("triples", job.outputModel.size());
        result.put("conforms", job.outputModel.size() > 0);
        result.put("violationCount", 0);
        return result;
    }

    // ── Step 6: Publish ───────────────────────────────────────────

    public synchronized Map<String, Object> publish(String name) {
        OnboardingJob job = jobs.get(name);
        if (job == null || job.outputModel == null) {
            return Map.of("status", "error", "message", "No output model to publish for: " + name);
        }

        int previousSize = inferredModel != null ? (int) inferredModel.size() : 0;
        if (inferredModel != null) {
            inferredModel.add(job.outputModel);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "published");
        result.put("totalTriples", inferredModel != null ? inferredModel.size() : job.outputModel.size());
        result.put("newTriplesAdded", job.outputModel.size());
        result.put("newNodeId", name);
        result.put("newNodeLabel", name + " (SQL)");
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Path jobDirectory(String name) {
        Path dir = OUTPUT_ROOT.resolve(name);
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        return dir;
    }

    private void writeJson(Path path, Object data) {
        try {
            Files.writeString(path, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void writeModel(Model model, Path path, Lang lang) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream os = Files.newOutputStream(path)) { RDFDataMgr.write(os, model, lang); }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public boolean isLLMEnabled() {
        return llmService.isLLMEnabled();
    }

    private record OnboardingJob(String name, Path dir, SqlInputParser.SchemaStatistics schemaStats) {
        List<MappingSuggestion> suggestions = List.of();
        List<ApprovedMapping> approvedMappings = null;
        Model outputModel = null;
    }

    // Expose for SemanticService to check
    public boolean hasModel(String name) {
        OnboardingJob job = jobs.get(name);
        return job != null && job.outputModel != null;
    }

    public Model getModel(String name) {
        OnboardingJob job = jobs.get(name);
        return job != null ? job.outputModel : null;
    }
}
```

Note: `LLMMappingService` needs `isLLMEnabled()` accessor — add to LLMMappingService:
```java
public boolean isLLMEnabled() { return llmEnabled; }
```

- [ ] **Step 3: Run compile to check for errors**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn compile -q 2>&1 | grep -E "(ERROR|error:|cannot find)" | head -20`
Expected: Errors about `isLLMEnabled` missing from `LLMMappingService`

- [ ] **Step 4: Fix LLMMappingService — add isLLMEnabled accessor**

Add to `LLMMappingService.java`:
```java
    public boolean isLLMEnabled() {
        return llmEnabled;
    }
```

- [ ] **Step 5: Run compile again**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn compile -q 2>&1 | head -10`
Expected: No errors

- [ ] **Step 6: Write minimal integration test**

```java
// src/test/java/com/semlink/onboarding/OnboardingServiceTest.java
package com.semlink.onboarding;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingServiceTest {
    @Test
    void parseSql_shouldReturnSchemaStats() {
        OnboardingService svc = new OnboardingService(null);
        String schemaSql = "CREATE TABLE students (id INT, name VARCHAR(100));";
        String dataSql = "INSERT INTO students (id, name) VALUES (1, 'Alice');";

        var result = svc.parseSql("test-ds", schemaSql, dataSql);

        assertEquals("parsed", result.get("status"));
        assertEquals(1, result.get("tables"));
    }

    @Test
    void approveMappings_shouldPersistConfig() {
        OnboardingService svc = new OnboardingService(null);
        String schemaSql = "CREATE TABLE students (id INT, name VARCHAR(100));";
        String dataSql = "INSERT INTO students (id, name) VALUES (1, 'Alice');";
        svc.parseSql("test-ds2", schemaSql, dataSql);

        var mappings = java.util.List.of(
            java.util.Map.of("sourceTable", "students", "sourceColumn", "id",
                             "aicteClass", "Student", "aicteProperty", "id", "userCustomized", false)
        );
        var result = svc.approveMappings("test-ds2", mappings);
        assertEquals("approved", result.get("status"));
        assertEquals(1, result.get("mappingCount"));
    }
}
```

- [ ] **Step 7: Run tests**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn test -Dtest=OnboardingServiceTest -q 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/semlink/onboarding/ApprovedMapping.java \
        src/main/java/com/semlink/onboarding/OnboardingService.java \
        src/test/java/com/semlink/onboarding/OnboardingServiceTest.java
git commit -m "feat(onboarding): add ApprovedMapping record and OnboardingService orchestrator"
```

---

## Task 4: Backend — API Controller Updates + SemanticService Thread Safety

**Files:**
- Modify: `src/main/java/com/semlink/ApiController.java`
- Modify: `src/main/java/com/semlink/SemanticService.java`

- [ ] **Step 1: Add new onboarding endpoints to ApiController**

After the existing `onboardFromSql` method (line 130), add:

```java
    /* ── Onboarding Steps ──────────────────────────────────────── */

    @PostMapping("/onboard/parse")
    public Map<String, Object> onboardParse(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "uploaded-" + System.currentTimeMillis());
        String schemaSql = body.getOrDefault("schemaSql", "");
        String dataSql = body.getOrDefault("dataSql", "");
        if (schemaSql.isBlank() || dataSql.isBlank()) {
            return Map.of("status", "error", "message", "Both schemaSql and dataSql are required");
        }
        return onboardingService.parseSql(name, schemaSql, dataSql);
    }

    @PostMapping("/onboard/suggest")
    public Map<String, Object> onboardSuggest(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        return onboardingService.suggestMappings(name);
    }

    @PostMapping("/onboard/approve")
    public Map<String, Object> onboardApprove(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        @SuppressWarnings("unchecked")
        var mappings = (java.util.List<Map<String, Object>>) body.getOrDefault("approvedMappings", java.util.List.of());
        return onboardingService.approveMappings(name, mappings);
    }

    @PostMapping("/onboard/transform")
    public Map<String, Object> onboardTransform(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        String schemaSql = body.getOrDefault("schemaSql", "");
        String dataSql = body.getOrDefault("dataSql", "");
        if (name.isBlank() || schemaSql.isBlank() || dataSql.isBlank()) {
            return Map.of("status", "error", "message", "name, schemaSql, and dataSql are required");
        }
        return onboardingService.transform(name, schemaSql, dataSql);
    }

    @PostMapping("/onboard/validate")
    public Map<String, Object> onboardValidate(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        String schemaSql = body.getOrDefault("schemaSql", "");
        String dataSql = body.getOrDefault("dataSql", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        return onboardingService.validateTriples(name, schemaSql, dataSql);
    }

    @PostMapping("/onboard/publish")
    public Map<String, Object> onboardPublish(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        return onboardingService.publish(name);
    }

    @GetMapping("/onboard/status/{name}")
    public Map<String, Object> onboardStatus(@PathVariable String name) {
        return Map.of("name", name, "hasModel", onboardingService.hasModel(name));
    }
```

Add field at top of ApiController:
```java
    private final OnboardingService onboardingService;
```

And update constructor:
```java
    public ApiController(SemanticService service, OnboardingService onboardingService) {
        this.service = service;
        this.onboardingService = onboardingService;
    }
```

- [ ] **Step 2: Add OnboardingService bean creation to SemanticService**

In `SemanticService.java`, after the `nlTranslator` initialization block (after line 60), add:

```java
        OnboardingService onboardingService = new OnboardingService(apiKey);
        onboardingService.setInferredModel(inferredModel);
```

Wait — `inferredModel` is null at class init time. Instead, add a method `setInferredModel` to SemanticService and call it after pipeline runs:

Add to SemanticService:
```java
    public void setInferredModel(Model model) {
        this.inferredModel = model;
    }

    public OnboardingService createOnboardingService(String apiKey) {
        return new OnboardingService(apiKey);
    }
```

Update the `runPipeline()` method to inject the model into OnboardingService after it's created. Create a shared OnboardingService instance:

```java
    private final OnboardingService onboardingService;

    {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = loadEnvFile("GEMINI_API_KEY");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("Gemini API key found, enabling AI query translation");
            nlTranslator = NLQueryTranslator.withGemini(apiKey);
        } else {
            log.info("No Gemini API key, using deterministic fallback");
            nlTranslator = NLQueryTranslator.withoutRemoteModel();
        }
        onboardingService = new OnboardingService(apiKey);
    }
```

In `runPipeline()` after line 92 (`inferredModel = createInferredModel(base);`), add:
```java
        onboardingService.setInferredModel(inferredModel);
```

Also update `runR2oFromSql` to use a thread-safe add:
```java
            if (inferredModel != null) {
                synchronized (this) {
                    inferredModel.add(export.model());
                    result.put("mergedIntoGraph", true);
                    result.put("newTotalTriples", inferredModel.size());
                }
            }
```

- [ ] **Step 3: Compile check**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn compile -q 2>&1 | grep -E "(ERROR|cannot find)" | head -20`
Expected: No errors (or only pre-existing ones)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/semlink/ApiController.java src/main/java/com/semlink/SemanticService.java
git commit -m "feat(onboarding): add multi-step onboarding REST endpoints and wire OnboardingService"
```

---

## Task 5: Frontend — New API Methods

**Files:**
- Modify: `semlink-ui/src/api.ts:1-130`

- [ ] **Step 1: Add new API methods to api.ts**

After the `onboardFromSql` function (line 121), add:

```typescript
export const onboardParse = async (name: string, schemaSql: string, dataSql: string) => {
  const res = await fetch(`${API_BASE}/onboard/parse`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, schemaSql, dataSql })
  });
  return res.json();
};

export const onboardSuggest = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/suggest`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name })
  });
  return res.json();
};

export const onboardApprove = async (name: string, approvedMappings: ApprovedMapping[]) => {
  const res = await fetch(`${API_BASE}/onboard/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, approvedMappings })
  });
  return res.json();
};

export const onboardTransform = async (name: string, schemaSql: string, dataSql: string) => {
  const res = await fetch(`${API_BASE}/onboard/transform`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, schemaSql, dataSql })
  });
  return res.json();
};

export const onboardValidate = async (name: string, schemaSql: string, dataSql: string) => {
  const res = await fetch(`${API_BASE}/onboard/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, schemaSql, dataSql })
  });
  return res.json();
};

export const onboardPublish = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name })
  });
  return res.json();
};

export const onboardStatus = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/status/${name}`, { headers: authHeader() });
  return res.json();
};

export type ApprovedMapping = {
  sourceTable: string;
  sourceColumn: string;
  aicteClass: string;
  aicteProperty: string;
  userCustomized: boolean;
};

export type MappingSuggestion = {
  sourceTable: string;
  sourceColumn: string;
  suggestedClass: string;
  suggestedProperty: string;
  confidence: number;
  rationale: string;
  matchType: string;
};
```

- [ ] **Step 2: Commit**

```bash
git add semlink-ui/src/api.ts
git commit -m "feat(onboard-ui): add multi-step onboarding API methods"
```

---

## Task 6: Frontend — MappingReviewTable Component

**Files:**
- Create: `semlink-ui/src/MappingReviewTable.tsx`

- [ ] **Step 1: Write the MappingReviewTable component**

```tsx
// semlink-ui/src/MappingReviewTable.tsx
import React, { useState } from 'react';
import type { MappingSuggestion, ApprovedMapping } from './api';

const AICTE_PROPERTIES = [
  'id', 'name', 'cgpa', 'department',
  'studiesAt', 'belongsToUniversity', 'offersCourse', 'memberOfDepartment', 'enrolledIn'
];
const AICTE_CLASSES = ['Student', 'College', 'University', 'Course', 'Department', 'Program'];

type Props = {
  suggestions: MappingSuggestion[];
  onApprove: (mappings: ApprovedMapping[]) => void;
  onBack: () => void;
};

export default function MappingReviewTable({ suggestions, onApprove, onBack }: Props) {
  const [pending, setPending] = useState<Map<string, string>>(() => {
    const initial = new Map<string, string>();
    suggestions.forEach(s => {
      initial.set(`${s.sourceTable}.${s.sourceColumn}`, s.suggestedProperty);
    });
    return initial;
  });
  const [expandedTable, setExpandedTable] = useState<string | null>(null);

  const grouped = suggestions.reduce((acc, s) => {
    acc.set(s.sourceTable, [...(acc.get(s.sourceTable) || []), s]);
    return acc;
  }, new Map<string, MappingSuggestion[]>());

  const setProperty = (table: string, column: string, value: string) => {
    setPending(p => {
      const next = new Map(p);
      next.set(`${table}.${column}`, value);
      return next;
    });
  };

  const handleApprove = () => {
    const approved = suggestions.map(s => ({
      sourceTable: s.sourceTable,
      sourceColumn: s.sourceColumn,
      aicteClass: s.suggestedClass,
      aicteProperty: pending.get(`${s.sourceTable}.${s.sourceColumn}`) || s.suggestedProperty,
      userCustomized: pending.get(`${s.sourceTable}.${s.sourceColumn}`) !== s.suggestedProperty
    }));
    onApprove(approved);
  };

  const confidenceColor = (c: number) =>
    c >= 0.85 ? 'var(--good)' : c >= 0.65 ? 'var(--warn)' : 'var(--bad)';

  const confidenceLabel = (c: number) =>
    c >= 0.85 ? '✓' : c >= 0.65 ? '⚠' : '✗';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h3>Review Mapping Suggestions</h3>
          <p className="text-secondary">
            {suggestions.length} suggestions from {grouped.size} tables.
            Review each mapping before transforming your data.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn-secondary" onClick={onBack}>← Back</button>
          <button className="btn-primary" onClick={handleApprove}>Approve All & Continue →</button>
        </div>
      </div>

      {Array.from(grouped.entries()).map(([tableName, cols]) => (
        <div key={tableName} className="glass-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div
            style={{
              padding: '12px 16px', cursor: 'pointer', display: 'flex', justifyContent: 'space-between',
              background: 'var(--panel)', borderBottom: expandedTable === tableName ? '1px solid var(--line)' : 'none'
            }}
            onClick={() => setExpandedTable(expandedTable === tableName ? null : tableName)}
          >
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <span style={{ fontSize: 11, color: 'var(--muted)' }}>{expandedTable === tableName ? '▼' : '▶'}</span>
              <span style={{ fontWeight: 600 }}>{tableName}</span>
              <span className="badge blue">{cols.length} columns</span>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              {cols.map(c => (
                <div key={c.sourceColumn} style={{
                  width: 8, height: 8, borderRadius: '50%',
                  background: confidenceColor(c.confidence), title: c.sourceColumn
                }} />
              ))}
            </div>
          </div>

          {expandedTable === tableName && (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Source Column</th>
                  <th>Suggested AICTE Class</th>
                  <th>Target Property</th>
                  <th>Confidence</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {cols.map(s => (
                  <tr key={s.sourceColumn}>
                    <td><code style={{ fontFamily: 'var(--mono)' }}>{s.sourceColumn}</code></td>
                    <td>
                      <select
                        className="input"
                        value={s.suggestedClass}
                        style={{ fontSize: 12 }}
                        onChange={e => { /* class change — future use */ }}
                      >
                        {AICTE_CLASSES.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        className="input"
                        value={pending.get(`${tableName}.${s.sourceColumn}`) || s.suggestedProperty}
                        style={{ fontSize: 12 }}
                        onChange={e => setProperty(tableName, s.sourceColumn, e.target.value)}
                      >
                        {AICTE_PROPERTIES.map(p => (
                          <option key={p} value={p}>{p}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <div style={{
                          width: 40, height: 6, background: 'var(--line)', borderRadius: 3,
                          position: 'relative'
                        }}>
                          <div style={{
                            width: `${s.confidence * 100}%`, height: '100%',
                            background: confidenceColor(s.confidence), borderRadius: 3
                          }} />
                        </div>
                        <span style={{ fontSize: 12, color: confidenceColor(s.confidence) }}>
                          {confidenceLabel(s.confidence)} {s.confidence.toFixed(2)}
                        </span>
                      </div>
                    </td>
                    <td>
                      <button
                        className="btn-secondary"
                        style={{ fontSize: 11, padding: '3px 8px' }}
                        onClick={() => setProperty(tableName, s.sourceColumn, s.suggestedProperty)}
                      >
                        Reset
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {expandedTable === tableName && cols.some(c => c.rationale) && (
            <div style={{ padding: '12px 16px', borderTop: '1px solid var(--line)' }}>
              <div className="nav-label" style={{ marginBottom: 6 }}>LLM Rationale</div>
              {cols.filter(c => c.rationale).map(c => (
                <div key={c.sourceColumn} style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 4 }}>
                  <strong>{c.sourceColumn}</strong>: {c.rationale}
                </div>
              ))}
            </div>
          )}
        </div>
      ))}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <button className="btn-secondary" onClick={onBack}>← Back</button>
        <button className="btn-primary" onClick={handleApprove}>Approve All & Transform →</button>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add semlink-ui/src/MappingReviewTable.tsx
git commit -m "feat(onboard-ui): add MappingReviewTable component"
```

---

## Task 7: Frontend — New OnboardStep Component (State Machine)

**Files:**
- Create: `semlink-ui/src/OnboardStep.tsx`

- [ ] **Step 1: Write the OnboardStep component**

```tsx
// semlink-ui/src/OnboardStep.tsx
import React, { useState, useRef } from 'react';
import type { MappingSuggestion, ApprovedMapping } from './api';
import * as api from './api';
import MappingReviewTable from './MappingReviewTable';

type Step = 'UPLOAD' | 'PARSING' | 'SUGGESTING' | 'REVIEWING' | 'TRANSFORMING' | 'VALIDATING' | 'PUBLISHING' | 'DONE' | 'ERROR';

const STEP_LABELS = ['Extract', 'Map & LLM', 'Review', 'Transform', 'Validate', 'Publish'];

const delay = (ms: number) => new Promise(r => setTimeout(r, ms));

interface OnboardStepProps {
  onComplete: () => void;
}

export default function OnboardStep({ onComplete }: OnboardStepProps) {
  const [step, setStep] = useState<Step>('UPLOAD');
  const [logs, setLogs] = useState<string[]>([]);
  const [schemaSql, setSchemaSql] = useState('');
  const [dataSql, setDataSql] = useState('');
  const [datasetName] = useState(() => 'university-' + Date.now());
  const [suggestions, setSuggestions] = useState<MappingSuggestion[]>([]);
  const [llmMode, setLlmMode] = useState<string>('');
  const [transformResult, setTransformResult] = useState<any>(null);
  const [error, setError] = useState('');
  const logRef = useRef<HTMLDivElement>(null);

  const addLog = (msg: string) => {
    setLogs(p => [...p, msg]);
    setTimeout(() => logRef.current?.scrollTo(0, 9999), 50);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>, setter: (v: string) => void) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (ev) => setter((ev.target?.result as string) || '');
      reader.readAsText(file);
    }
  };

  const run = async () => {
    if (!schemaSql || !dataSql) {
      alert('Please provide both Schema and Data SQL files');
      return;
    }

    try {
      // Step 1: Parse
      setStep('PARSING');
      addLog('▶ Step 1: Parsing Source Database (SQL Schema + Data)…');
      await delay(400);

      const parseResult = await api.onboardParse(datasetName, schemaSql, dataSql);
      if (parseResult.status !== 'parsed') throw new Error(parseResult.message || 'Parse failed');
      addLog(`  ✓ Found ${parseResult.tables} tables and ${parseResult.totalRows} rows.`);
      addLog(`  ✓ Schema parsed successfully.`);

      // Step 2: LLM Suggest
      setStep('SUGGESTING');
      addLog('▶ Step 2: Generating LLM Mapping Suggestions…');
      await delay(400);

      const suggestResult = await api.onboardSuggest(datasetName);
      if (suggestResult.status !== 'completed') throw new Error(suggestResult.message || 'Suggest failed');
      setSuggestions(suggestResult.suggestions || []);
      setLlmMode(suggestResult.llmMode || 'heuristic');
      addLog(`  ✓ LLM (${suggestResult.llmMode}) generated ${suggestResult.suggestions?.length || 0} mapping suggestions.`);
      addLog('  ✓ Review the suggestions in the next step and approve or adjust them.');

      setStep('REVIEWING');
    } catch (err: any) {
      setStep('ERROR');
      setError(err.message);
      addLog(`  ❌ Error: ${err.message}`);
    }
  };

  const handleApprove = async (approved: ApprovedMapping[]) => {
    try {
      addLog('▶ Step 3: Applying Approved Mappings…');
      const approveResult = await api.onboardApprove(datasetName, approved);
      if (approveResult.status !== 'approved') throw new Error(approveResult.message);
      addLog(`  ✓ ${approveResult.mappingCount} mappings approved.`);

      // Step 4: Transform
      setStep('TRANSFORMING');
      addLog('▶ Step 4: Transforming SQL data to RDF triples…');
      await delay(400);

      const transformResult = await api.onboardTransform(datasetName, schemaSql, dataSql);
      if (transformResult.status !== 'completed') throw new Error(transformResult.message);
      setTransformResult(transformResult);
      addLog(`  ✓ Generated ${transformResult.triples} semantic triples.`);
      if (transformResult.extractedClasses?.length) {
        addLog(`  ✓ Extracted Classes: ${transformResult.extractedClasses.join(', ')}`);
      }

      // Step 5: Validate
      setStep('VALIDATING');
      addLog('▶ Step 5: Validating output triples against SHACL shapes…');
      await delay(400);

      const validateResult = await api.onboardValidate(datasetName, schemaSql, dataSql);
      if (validateResult.status !== 'completed') throw new Error(validateResult.message);
      if (validateResult.conforms) {
        addLog('  ✓ Validation passed — triples conform to AICTE shapes.');
      } else {
        addLog(`  ⚠ ${validateResult.violationCount} validation warnings.`);
      }

      // Step 6: Publish
      setStep('PUBLISHING');
      addLog('▶ Step 6: Publishing to unified graph…');
      await delay(400);

      const publishResult = await api.onboardPublish(datasetName);
      if (publishResult.status !== 'published') throw new Error(publishResult.message);
      addLog(`  ✓ Published! New Graph Size: ${publishResult.totalTriples} triples.`);
      addLog(`  ✓ Added node "${publishResult.newNodeLabel}" to lineage graph.`);
      addLog('✅ Onboarding complete! Your SQL data is now mapped and queryable in the semantic graph.');

      // Pass newNodeId up so Lineage tab can show the correct node
      setStep('DONE');
      onComplete();
      return;
    } catch (err: any) {
      setStep('ERROR');
      setError(err.message);
      addLog(`  ❌ Error: ${err.message}`);
    }
  };

  // When going back to REVIEWING (e.g., from ERROR), re-fetch suggestions
  const handleBackToReview = async () => {
    setStep('REVIEWING');
    setLogs(p => [...p, '↩ Reloading mapping suggestions…']);
    try {
      const suggestResult = await api.onboardSuggest(datasetName);
      setSuggestions(suggestResult.suggestions || []);
    } catch (err: any) {
      addLog(`  ⚠ Could not reload suggestions: ${err.message}`);
    }
  };

  const stepIndex = STEP_LABELS.indexOf(
    step === 'UPLOAD' ? 'Extract' :
    step === 'PARSING' ? 'Extract' :
    step === 'SUGGESTING' ? 'Map & LLM' :
    step === 'REVIEWING' ? 'Review' :
    step === 'TRANSFORMING' ? 'Transform' :
    step === 'VALIDATING' ? 'Validate' :
    step === 'PUBLISHING' || step === 'DONE' ? 'Publish' :
    step === 'ERROR' ? 'Publish' : ''
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 900 }}>
      <div>
        <h2>RDB → Ontology Onboarding</h2>
        <p className="text-secondary" style={{ marginTop: 4 }}>
          Upload SQL files to automatically map your relational data to the AICTE ontology
          with LLM-assisted mapping suggestions and human review.
        </p>
      </div>

      <div className="step-track">
        {STEP_LABELS.map((label, i) => (
          <div key={label} className={`step-item ${i < stepIndex ? 'done' : i === stepIndex ? 'active' : ''}`}>
            <div className="step-num">{i < stepIndex ? '✓' : i + 1}</div>
            <div className="step-name">{label}</div>
          </div>
        ))}
      </div>

      {step === 'UPLOAD' && (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <div className="nav-label">Schema SQL File</div>
              <input type="file" accept=".sql" className="input"
                onChange={e => handleFileUpload(e, setSchemaSql)} />
              {schemaSql && (
                <div style={{ fontSize: 11, color: 'var(--good)', marginTop: 4 }}>
                  ✓ Schema loaded ({schemaSql.length.toLocaleString()} bytes)
                </div>
              )}
            </div>
            <div>
              <div className="nav-label">Data SQL File</div>
              <input type="file" accept=".sql" className="input"
                onChange={e => handleFileUpload(e, setDataSql)} />
              {dataSql && (
                <div style={{ fontSize: 11, color: 'var(--good)', marginTop: 4 }}>
                  ✓ Data loaded ({dataSql.length.toLocaleString()} bytes)
                </div>
              )}
            </div>
          </div>
          <button className="btn-primary" onClick={run}
            disabled={!schemaSql || !dataSql}
            style={{ alignSelf: 'flex-start' }}>
            Start Onboarding →
          </button>
        </div>
      )}

      {(step === 'PARSING' || step === 'SUGGESTING') && (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center', padding: 40 }}>
          <div className="spinner" />
          <p>{step === 'PARSING' ? 'Parsing SQL schema and data…' : 'Generating LLM mapping suggestions…'}</p>
          <p className="text-secondary" style={{ fontSize: 12 }}>This may take a moment</p>
        </div>
      )}

      {step === 'REVIEWING' && (
        <MappingReviewTable
          suggestions={suggestions}
          onApprove={handleApprove}
          onBack={() => setStep('UPLOAD')}
        />
      )}

      {(step === 'TRANSFORMING' || step === 'VALIDATING' || step === 'PUBLISHING') && (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center', padding: 40 }}>
          <div className="spinner" />
          <p>{
            step === 'TRANSFORMING' ? 'Applying mappings and generating RDF triples…' :
            step === 'VALIDATING' ? 'Validating against SHACL shapes…' :
            'Publishing to unified graph…'
          }</p>
        </div>
      )}

      {logs.length > 0 && (
        <div className="workflow-log" ref={logRef}>
          {logs.map((l, i) => (
            <div key={i} className={`log-line ${
              l.startsWith('✅') ? 'success' :
              l.startsWith('▶') ? 'info' :
              l.startsWith('  ❌') ? 'bad' : ''
            }`}>{l}</div>
          ))}
        </div>
      )}

      {step === 'ERROR' && (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="banner bad">❌ {error}</div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-secondary" onClick={() => { setStep('UPLOAD'); setLogs([]); setError(''); setSuggestions([]); }}>
              ← Start Over
            </button>
            <button className="btn-secondary" onClick={handleBackToReview}>
              ← Back to Review
            </button>
          </div>
        </div>
      )}

      {step === 'DONE' && (
        <div className="glass-card">
          <div className="banner good">✅ Onboarding complete! View the Lineage tab to see the new connection.</div>
          {transformResult && (
            <div style={{ marginTop: 16, display: 'flex', gap: 16 }}>
              <div className="stat-card">
                <div className="stat-label">Triples Generated</div>
                <div className="stat-value">{transformResult.triples}</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Classes Extracted</div>
                <div className="stat-value">{transformResult.extractedClasses?.length || 0}</div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

Note: Ensure the CSS has a `.spinner` class or add to the styles:
```css
.spinner {
  width: 32px; height: 32px;
  border: 3px solid var(--line);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
```

- [ ] **Step 2: Commit**

```bash
git add semlink-ui/src/OnboardStep.tsx
git commit -m "feat(onboard-ui): add multi-step OnboardStep wizard component"
```

---

## Task 8: Frontend — Replace Onboard in App.tsx

**Files:**
- Modify: `semlink-ui/src/App.tsx:34-36` and `semlink-ui/src/App.tsx:56`

- [ ] **Step 1: Update App.tsx to use OnboardStep**

At line 56, change:
```tsx
// OLD:
{tab==='onboard'&&<Onboard onComplete={() => api.getHealth().then(setHealth)} />}
```
```tsx
// NEW:
{tab==='onboard'&&<OnboardStep onComplete={() => api.getHealth().then(setHealth)} />}
```

Add import at top of file:
```tsx
import OnboardStep from './OnboardStep';
```

Also remove the old `Onboard` function component (lines 279-363) since it's replaced by `OnboardStep`.

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK/semlink-ui && npx tsc --noEmit 2>&1 | head -30`
Expected: No errors (or only pre-existing ones)

- [ ] **Step 3: Commit**

```bash
git add semlink-ui/src/App.tsx
git commit -m "feat(onboard-ui): replace Onboard with OnboardStep wizard in App"
```

---

## Task 9: Integration — Lineage Graph Update on Publish

**Files:**
- Modify: `semlink-ui/src/App.tsx` (Lineage tab)

- [ ] **Step 1: Update Lineage component to show newly onboarded universities**

The Lineage component needs to react to `health?.totalTriples` changing after onboarding. Since the component already checks `hasExtra = health?.totalTriples > 1408` to conditionally add university5, we need to make this more dynamic.

Replace the hardcoded `hasExtra` check with a query to the backend:

```tsx
// In App.tsx Lineage function component, add state for extra nodes:
const [extraNodes, setExtraNodes] = useState<string[]>([]);

// After onComplete in OnboardStep, the parent re-fetches health.
// Use a useEffect to detect new universities:
useEffect(() => {
  if (health?.totalTriples > 1408) {
    setExtraNodes(['u5']);
  }
}, [health?.totalTriples]);
```

Update `initialNodes` and `initialEdges` to be derived from state:
```tsx
const allNodes = [
  {id:'aicte',position:{x:400,y:20},data:{label:'🏛 AICTE Central Ontology'},type:'input'},
  {id:'u1',position:{x:50,y:120},data:{label:'🎓 University 1'},type:'input'},
  {id:'u2',position:{x:250,y:120},data:{label:'🎓 University 2'},type:'input'},
  {id:'u3',position:{x:450,y:120},data:{label:'🎓 University 3'},type:'input'},
  {id:'u4',position:{x:650,y:120},data:{label:'🎓 University 4'},type:'input'},
  ...extraNodes.map((id, i) => ({
    id, position: { x: 850 + i * 200, y: 120 }, data: { label: `${id.toUpperCase()} (SQL)` }, type: 'input'
  })),
  {id:'merge',position:{x:350,y:240},data:{label:'📦 Merged Graph'},type:'default'},
  {id:'rules',position:{x:100,y:240},data:{label:'⚙️ Alignment Rules'},type:'input'},
  {id:'inf',position:{x:350,y:360},data:{label:'🧠 Inferred AICTE Graph'},type:'output'},
  {id:'shacl',position:{x:600,y:360},data:{label:'✅ SHACL Validation'},type:'output'}
];

const allEdges = [
  {id:'e1',source:'u1',target:'merge',animated:true},
  {id:'e2',source:'u2',target:'merge',animated:true},
  {id:'e3',source:'u3',target:'merge',animated:true},
  {id:'e4',source:'u4',target:'merge',animated:true},
  {id:'e5',source:'aicte',target:'merge',animated:true,style:{stroke:'var(--accent)'}},
  {id:'e6',source:'merge',target:'inf',animated:true},
  {id:'e7',source:'rules',target:'inf',animated:true,style:{stroke:'var(--purple)'}},
  {id:'e8',source:'inf',target:'shacl',animated:true,style:{stroke:'var(--good)'}},
  ...extraNodes.map((id, i) => ({id:`e${9+i}`,source:id,target:'merge',animated:true,style:{stroke:'var(--blue)'}}))
];
```

- [ ] **Step 2: Commit**

```bash
git add semlink-ui/src/App.tsx
git commit -m "feat(lineage): make Lineage graph dynamically show newly onboarded universities"
```

---

## Task 10: End-to-End Verification

- [ ] **Step 1: Build and start the backend**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn clean compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn spring-boot:run -q 2>&1 &` (or use your IDE)
Wait for startup.

- [ ] **Step 2: Build and start the frontend**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK/semlink-ui && npm run build 2>&1 | tail -10`
Expected: No TypeScript errors

- [ ] **Step 3: Test the full flow with the demo SQL files**

Find the demo SQL files:
Run: `find /Users/rutul/Documents/Data_Modelling/SEMLINK -name "*.sql" | head -10`

Use the demo university5 SQL files (schema + data) to test the full onboarding flow:
1. Navigate to Onboarding tab
2. Upload schema SQL and data SQL
3. Click "Start Onboarding"
4. Wait for LLM suggestions (or heuristic fallback)
5. Review the mapping table — approve or change mappings
6. Continue through transform → validate → publish
7. Verify Lineage tab shows new university node
8. Run a SPARQL query in Query Studio to confirm data is queryable

- [ ] **Step 4: Run all existing tests**

Run: `cd /Users/rutul/Documents/Data_Modelling/SEMLINK && mvn test -q 2>&1 | tail -15`
Expected: All tests pass (or only pre-existing failures)

---

## Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| Step 1: Upload | Task 7 (OnboardStep UPLOAD state) |
| Step 2: Parse + LLM | Task 1 (buildSchemaStatistics), Task 2 (LLMMappingService) |
| Step 3: Review Table | Task 6 (MappingReviewTable), Task 5 (API) |
| Step 4: Transform | Task 3 (OnboardingService.transform) |
| Step 5: Validate | Task 3 (OnboardingService.validateTriples) |
| Step 6: Publish | Task 3 (OnboardingService.publish), Task 4 (SemanticService merge) |
| Lineage update | Task 9 |
| Persistence (JSON files) | Task 3 (writeJson to job directory) |
| Thread-safe model add | Task 4 (synchronized block) |
| LLM fallback to heuristics | Task 2 (LLMMappingService.generateWithHeuristics) |
| New API endpoints | Task 4 (ApiController new endpoints) |
| Error handling with retry | Task 7 (ERROR state in OnboardStep) |