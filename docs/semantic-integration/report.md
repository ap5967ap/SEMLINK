# Semantic Integration Report

## Project Goal

This project demonstrates how four heterogeneous university data models can be integrated semantically without physically merging their databases. The integration layer is a central AICTE ontology. Each university keeps its own vocabulary, while OWL mappings and reasoning expose a unified AICTE view for SPARQL querying.

## Ontology Set

| Ontology | Source | Modeling Style | Reuse Strategy |
| --- | --- | --- | --- |
| University 1 | Existing repo ontology, now stored at `semantic/ontologies/local/university1/university1.ttl` | `Student`, `College`, `Course`, `Department`, `Program` | Reused directly as the richest local ontology |
| University 2 | Existing repo seed, now preserved under `semantic/ontologies/local/university2/reference/` and curated into `university2.ttl` | `Learner`, `Institute`, `Module`, `School`, `Track` | Curated and normalized from the repo file |
| University 3 | New | `Pupil`, `CampusCollege`, `Subject`, `FacultyArea` | Added to increase semantic heterogeneity |
| University 4 | New | `StudentInfo`, `AffiliatedCollege`, `Paper`, `Division`, `AcademicPlan` | Added with an indirect student-to-college structure |
| AICTE | New, stored at `semantic/ontologies/central/aicte.ttl` | `Student`, `College`, `University`, `Course`, `Department`, `Program` | Central integration ontology |

## Folder Structure

The repository is organized so the semantic assets sit in one place:

- `semantic/ontologies/central/` for the AICTE ontology
- `semantic/ontologies/local/` for university ontologies and preserved reference files
- `semantic/ontologies/support/` for helper datasets such as invalid validation data
- `semantic/queries/core/` for the main demo queries
- `semantic/queries/analysis/` for aggregate/reporting queries
- `semantic/queries/identity/` for `owl:sameAs` exploration
- `semantic/rules/` for the reasoning rules
- `semantic/shapes/` for SHACL validation

## How Integration Works

1. The AICTE ontology declares the standard academic vocabulary.
2. Local classes and properties are aligned with AICTE using `owl:equivalentClass` and `owl:equivalentProperty`.
3. Selected duplicate entities are linked with `owl:sameAs`.
4. A controlled Jena rule layer materializes the AICTE view for querying.
5. SHACL validates the integrated result.

## Conflict Resolution Used

- Synonym conflict: `Learner`, `Pupil`, and `StudentInfo` are aligned to `aicte:Student`.
- Structural conflict: University 4 stores student-to-college through `pursuesProgram` and `programHostedBy`; the reasoning layer infers `aicte:studiesAt`.
- Identity conflict: duplicate entities are linked with `owl:sameAs`, including one reused student pair and one reused college pair.

## Generated Results

The latest demo run produced these artifacts in `target/semantic-output/`:

- 5 exported OWL files in RDF/XML format
- `merged.ttl` and `inferred.ttl`
- 6 named query result files
- `mapping-suggestions.tsv`
- `validation/valid-report.ttl`
- `validation/invalid-report.ttl`
- `summary.txt`

Current inferred summary:

- Students inferred: 24
- Colleges inferred: 7
- Courses inferred: 18
- Universities inferred: 4
- Curated merged model SHACL validation: `true`
- Merged model plus invalid sample SHACL validation: `false`

## Example Unified Queries

The query set uses only the AICTE vocabulary and now includes core, analysis, and identity queries:

- `all_students`
- `students_in_computer_science`
- `colleges_by_university`
- `courses_by_college`
- `student_college_resolution`
- `student_count_by_university`
- `student_count_by_department`
- `course_count_by_college`
- `department_to_college_map`
- `same_as_clusters`
- `same_as_student_details`

Example outcomes from the generated output:

- Students are returned from all four universities through the same AICTE schema.
- Colleges are grouped by university even though the local ontologies use different names and structures.
- University 4 student-college links appear through inference rather than explicit local triples.
- `owl:sameAs` reveals three explicit identity links across ontologies.

## Validation and Bonus Features

- SHACL is used to enforce minimum AICTE requirements for students, colleges, and courses.
- The curated merged model passes validation.
- A separate invalid sample intentionally fails validation to demonstrate constraint checking.
- `mapping-suggestions.tsv` shows Levenshtein-based suggestions with synonym boosts, including mappings such as `Learner -> Student`, `Module -> Course`, and `registeredAt -> studiesAt`.

## How To Run

```bash
mvn -q exec:java -Dexec.args="demo"
```

Single-query execution:

```bash
mvn -q exec:java -Dexec.args="query all_students"
```

Validation only:

```bash
mvn -q exec:java -Dexec.args="validate"
```

For a fuller list of the grouped queries, see `docs/semantic-integration/query-catalog.md`.

For a diagram-rich conceptual explanation, see `docs/semantic-integration/deep-dive.md`.
