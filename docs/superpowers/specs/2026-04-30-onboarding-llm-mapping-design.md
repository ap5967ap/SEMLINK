# End-to-End Onboarding Flow — Design Spec
**Date:** 2026-04-30
**Status:** Draft

---

## 1. Problem Statement

The current onboarding flow:
1. Skips LLM mapping suggestions — uses hardcoded `CLASS_HINTS` instead
2. Has no user review step — triples go directly into the pipeline
3. Shows extracted classes at the end but offers no way to correct wrong mappings
4. Data is merged into the inferred model only if the pipeline has already run

This spec covers a complete rewrite of the onboarding flow with interactive LLM-assisted mapping review.

---

## 2. High-Level Flow

```
Step 1: Upload       → User uploads Schema SQL + Data SQL files
       ↓
Step 2: Parse + LLM  → Parse SQL schema, generate statistics, send to Gemini
       ↓              Get back mapping suggestions with confidence scores
Step 3: Review       → User reviews/edits/approves each mapping in a table
       ↓
Step 4: Transform    → Apply approved mappings to produce RDF triples
       ↓
Step 5: Validate     → Run SHACL validation on the new triples
       ↓
Step 6: Publish      → Merge into the running inferred model + update lineage graph
```

---

## 3. Step-by-Step

### Step 1 — Upload (frontend + backend)
**Frontend:** No change. User uploads `schema.sql` and `data.sql` files.

**Backend:** `POST /api/v1/onboard/sql/parse`
- Input: `{ name, schemaSql, dataSql }`
- Parse SQL with `SqlInputParser` to extract:
  - Tables and their columns (name, type, nullable, default)
  - Primary keys (by naming convention: `*_id`, `id`)
  - Foreign keys
  - Row counts per table
  - Sample values per column (first 5 non-null values)
- Output: `ParsedSchema` containing tables, columns, statistics, sample values

**Key change:** `SqlInputParser` must also produce the statistics payload for LLM, not just the internal model.

---

### Step 2 — LLM Mapping Suggestion (new backend endpoint)

**Backend:** `POST /api/v1/onboard/suggest`
- Input: `{ name, schemaSql, dataSql }` (or reuse from step 1)
- Build schema statistics (no raw data values):
  ```json
  {
    "tables": [
      {
        "name": "students",
        "columns": [
          { "name": "student_id", "type": "INT", "nullable": false, "sample": ["S001", "S002"] },
          { "name": "full_nm", "type": "VARCHAR", "nullable": false, "sample": ["Alice Gupta", "Bob Singh"] },
          { "name": "dept_name", "type": "VARCHAR", "nullable": true, "sample": ["Computer Science", "Physics"] },
          { "name": "brn_cd", "type": "INT", "nullable": true, "sample": ["CS", "PH"] }
        ],
        "primaryKey": "student_id",
        "foreignKeys": [{ "column": "brn_cd", "references": "departments.dept_id" }],
        "rowCount": 47
      }
    ],
    "ontologyClasses": ["Student", "College", "University", "Course", "Department", "Program"],
    "ontologyProperties": [
      "studiesAt", "belongsToUniversity", "offersCourse", "memberOfDepartment",
      "enrolledIn", "name", "id", "cgpa", "department"
    ]
  }
  ```
- Send to Gemini with prompt: map source schema to AICTE ontology
- Response: list of `MappingSuggestion` objects:
  ```json
  {
    "sourceTable": "students",
    "sourceColumn": "full_nm",
    "suggestedClass": "Student",
    "suggestedProperty": "name",
    "confidence": 0.94,
    "rationale": "Column name 'full_nm' matches 'name' pattern. Data values are person names."
  }
  ```
- If Gemini unavailable, fall back to `R2oAssistant` logic (current behavior)
- Output: `{ status, suggestions: MappingSuggestion[] }`

---

### Step 3 — User Mapping Review (frontend + backend)

**Frontend:** Replace the current Step 2 "Generating Direct R2RML Mappings" log display with a **Mapping Review Table**.

#### UI Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Mapping Review — 4 tables detected                                           │
│ LLM generated suggestions below. Review and approve each mapping before      │
│ continuing.                                                                  │
├──────────────┬──────────────────────┬──────────────┬─────────────────────────┤
│ Table        │ Source Column        │ AICTE Target │ Confidence │ Actions   │
├──────────────┼──────────────────────┼──────────────┼────────────┼────────────┤
│ ▼ students   │ student_id           │ aicte:id     │ 0.99  ✓    │ [Approved] │
│   [expand]   │ full_nm             │ aicte:name   │ 0.94  ✓    │ [Approved] │
│              │ dept_name           │ [dropdown▼]  │ 0.71  ⚠    │ [Change]   │
│              │ brn_cd              │ aicte:dept   │ 0.68  ⚠    │ [Change]   │
├──────────────┼──────────────────────┼──────────────┼────────────┼────────────┤
│ ▼ departments│ dept_id             │ aicte:id     │ 0.99  ✓    │ [Approved] │
│   [expand]   │ dept_name           │ aicte:name   │ 0.96  ✓    │ [Approved] │
└──────────────┴──────────────────────┴──────────────┴────────────┴────────────┘

[Select missing columns from parsed SQL] → [+ Add Column Mapping]
```

**Key UI features:**
- Table rows are expandable to show the LLM rationale
- Confidence shown as a colored bar:
  - **Green (≥0.85):** Likely correct — pre-approved, user can still override
  - **Yellow (0.65–0.84):** Needs review — shown with ⚠ icon
  - **Red (<0.65):** Low confidence — user must explicitly approve
- "Change" button opens dropdown of all AICTE classes/properties for that column type
- User can also add mappings for columns the LLM didn't suggest (via "+ Add Column Mapping")
- "Approve All" button at bottom (approves all green-confidence suggestions)
- "Reject All" button to discard and redo

**Backend:** `POST /api/v1/onboard/approve`
- Input: `{ name, approvedMappings: ApprovedMapping[], customMappings: CustomMapping[] }`
- `ApprovedMapping`: `{ table, column, aicteClass, aicteProperty }`
- `CustomMapping`: same structure for user-added mappings
- Backend stores approved mapping configuration as JSON to `target/semantic-output/r2o/<name>/mapping-config.json`
- Output: `{ status, mappingCount }`

---

### Step 4 — Transform

**Backend:** Apply approved mappings to produce RDF triples.

The current `R2oRawExporter` produces raw triples (direct projection). The new flow:
1. Load the approved mapping config
2. For each `ApprovedMapping`:
   - Find rows from parsed data matching that table
   - Create RDF triple: `<sourceURI> <aicte:property> <value>`
3. For each `CustomMapping`, same process
4. Output to `target/semantic-output/r2o/<name>/approved-mapping.ttl`

Key change: `R2oRawExporter` must accept a mapping config and produce the AICTE-grounded triples directly (not raw triples that then need manual review).

---

### Step 5 — Validate

**Backend:** Uses existing `SemanticService.runValidation()` on the newly produced triples.

**Frontend:** Show SHACL validation report. If violations exist, show them with severity and focus node, and allow user to go back to Step 3 to fix mappings.

---

### Step 6 — Publish

**Backend:** `POST /api/v1/onboard/publish`
1. Load the approved-mapping triples from `target/semantic-output/r2o/<name>/approved-mapping.ttl`
2. Merge into the running `inferredModel` in `SemanticService`:
   ```java
   inferredModel.add(approvedTriples);
   ```
3. Update the lineage graph (add new node for `university5` connected to `merge`)
4. Return `{ status, totalTriples, newTriples }`

**Frontend:** Success message with link to Lineage tab. "View the new connection" button.

---

## 4. API Changes Summary

| Method | Endpoint | Purpose | New? |
|--------|----------|---------|------|
| POST | `/onboard/sql/parse` | Parse SQL, return schema stats | Yes |
| POST | `/onboard/suggest` | LLM mapping suggestions | Yes |
| POST | `/onboard/approve` | User approves mappings | Yes |
| POST | `/onboard/transform` | Apply mappings, produce RDF | Yes |
| POST | `/onboard/validate` | Validate new triples | Yes |
| POST | `/onboard/publish` | Merge into running model | Yes |
| GET | `/onboard/status/{name}` | Get onboarding job status | Yes |

All existing endpoints remain unchanged for backward compatibility with the demo.

---

## 5. Data Model Changes

### New Java Record: `MappingSuggestion`
```java
public record MappingSuggestion(
    String sourceTable,
    String sourceColumn,
    String suggestedClass,
    String suggestedProperty,
    double confidence,
    String rationale,
    String matchType  // "class", "property", "relationship"
) {}
```

### New Java Record: `ApprovedMapping`
```java
public record ApprovedMapping(
    String sourceTable,
    String sourceColumn,
    String aicteClass,
    String aicteProperty,
    boolean userCustomized
) {}
```

### New Java Record: `SchemaStatistics`
```java
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

public record ColumnStats(
    String name,
    String sqlType,
    boolean nullable,
    List<String> sampleValues
) {}
```

---

## 6. SQL Parser Enhancement

`SqlInputParser` must gain a new method:
```java
public SchemaStatistics buildSchemaStatistics(String schemaSql, String dataSql)
```

This extracts table/column info plus sample values for LLM context — without exposing raw INSERT data.

---

## 7. LLM Prompt Design (Gemini)

```
You are an expert in relational-to-ontology mapping. Given a database schema, map each table column to an AICTE ontology term.

ONTOLOGY CONTEXT:
- Classes: Student, College, University, Course, Department, Program
- Properties:
  * Data: id, name, cgpa, department (string)
  * Object: studiesAt, belongsToUniversity, offersCourse, memberOfDepartment, enrolledIn

SCHEMA:
{schema statistics JSON}

TASK:
For each column in the schema, suggest:
1. Which AICTE class this table likely represents (if not already suggested)
2. Which AICTE property this column maps to
3. Any object property relationships via foreign keys

Output a JSON array of mapping suggestions. For each:
- sourceTable, sourceColumn, suggestedClass, suggestedProperty, confidence (0.0-1.0), rationale
- confidence thresholds: >0.85 = high, 0.65-0.84 = medium, <0.65 = low

Only output the JSON array. No markdown. No explanation.
```

---

## 8. Frontend State Machine (Onboard Component)

```
States: UPLOAD → PARSING → SUGGESTING → REVIEWING → TRANSFORMING → VALIDATING → PUBLISHING → DONE

UPLOAD:       Show file upload form
PARSING:      Call /onboard/sql/parse, show spinner
SUGGESTING:   Call /onboard/suggest, show spinner with "LLM analyzing..."
REVIEWING:    Show Mapping Review Table
TRANSFORMING: Call /onboard/transform, show spinner
VALIDATING:   Call /onboard/validate, show spinner
PUBLISHING:   Call /onboard/publish, show spinner
DONE:         Show success banner with "View Lineage" button
ERROR:        Show error with "Retry" and "Back to Review" buttons
```

**Critical UX:** If user goes back from REVIEWING to UPLOAD and changes SQL files, the LLM suggestions must be re-fetched. The mapping config from a previous review session must NOT be reused without re-approval.

---

## 9. Error Handling

| Scenario | Behavior |
|----------|----------|
| LLM timeout or API error | Fall back to `R2oAssistant` heuristics, show banner "LLM unavailable, showing heuristic suggestions" |
| No mappings suggested for a table | Show table with "No suggestions — add mappings manually" + "+ Add" button |
| SHACL violations after transform | Show violation details, offer "Back to Review" to fix mappings |
| Pipeline not yet run | Proceed anyway — onboarding creates its own model, merge happens on publish |
| Large SQL file (>1MB) | Reject at upload with message "File too large" |

---

## 10. File Outputs

All written to `target/semantic-output/r2o/<name>/`:

| File | Contents |
|------|----------|
| `parsed-schema.json` | Output of `/onboard/sql/parse` |
| `llm-suggestions.json` | Output of `/onboard/suggest` |
| `mapping-config.json` | Approved mappings from `/onboard/approve` |
| `approved-mapping.ttl` | RDF triples after transform |
| `validation-report.ttl` | SHACL validation result |
| `summary.txt` | Human-readable summary |

---

## 11. Lineage Graph Update

On successful publish, the frontend `Lineage` tab must show the new university as a node connected to the merge point.

Backend must return the new node info:
```json
{
  "newNodeId": "university5",
  "newNodeLabel": "University 5 (SQL)",
  "totalTriples": 1847,
  "newTriplesAdded": 312
}
```

Frontend uses this to dynamically add `{id:'u5', ...}` to `initialNodes` and `{id:'e9', source:'u5', target:'merge'}` to `initialEdges`.

---

## 12. Implementation Order

1. Backend: `SchemaStatistics` record + `SqlInputParser.buildSchemaStatistics()`
2. Backend: `POST /onboard/sql/parse` endpoint
3. Backend: `POST /onboard/suggest` with Gemini prompt + fallback
4. Frontend: New `Onboard` component with state machine (steps 1–3)
5. Backend: `POST /onboard/approve` + persistence
6. Backend: Transform logic using approved mappings
7. Backend: `POST /onboard/transform`, `POST /onboard/validate`, `POST /onboard/publish`
8. Frontend: Steps 4–6 in `Onboard` component
9. Frontend: Update Lineage graph with new node on publish
10. Backend: Ensure `inferredModel.add()` is thread-safe and handles concurrent onboarding

---

## 13. Backward Compatibility

- Existing `POST /onboard/sql` continues to work (raw mode, no review)
- Existing demo buttons and API consumers are unaffected
- The new review flow is opt-in via the full onboarding wizard