# Architecture

```mermaid
flowchart LR
    U1["University 1\nlocal/university1/university1.ttl"] --> A["Central AICTE Ontology"]
    U2["University 2\nlocal/university2/university2.ttl"] --> A
    U3["University 3\nPupil / CampusCollege / Subject"] --> A
    U4["University 4\nStudentInfo / AffiliatedCollege / Paper"] --> A
    A --> R["Rule-Based Reasoning Layer"]
    R --> Q["AICTE-Only SPARQL Queries"]
    R --> V["SHACL Validation"]
    R --> S["Similarity Suggestions"]
    Q --> O["target/semantic-output"]
    V --> O
    S --> O
```

## Runtime Flow

1. Load the four university ontologies plus the AICTE ontology.
2. Merge them into one RDF model without physically flattening the source files.
3. Apply controlled rules to materialize AICTE-aligned classes and properties.
4. Run named SPARQL queries using only the AICTE vocabulary.
5. Validate the inferred model with SHACL.
6. Export merged and inferred snapshots plus RDF/XML `.owl` copies for submission.

## Resource Layout

- `semantic/ontologies/central/` holds the AICTE ontology.
- `semantic/ontologies/local/` holds the four university ontologies plus preserved reference material for the reused repo sources.
- `semantic/ontologies/support/` holds validation-only helper data such as the invalid sample.
- `semantic/queries/core/`, `analysis/`, and `identity/` group the SPARQL files by purpose.
