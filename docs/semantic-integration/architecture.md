# Architecture

```mermaid
flowchart LR
    DB["Optional Step 0\nRelational Database Input"] --> M1["Manual R2RML Mapping"]
    DB --> M2["Direct Raw RDF Export"]
    M2 --> M3["Assisted RDF Refinement"]
    C0["Optional Step 0\nCollege OWL + Mapping Rules"] --> C1["Custom Onboarding Workflow"]
    M1 --> U0["Generated RDF or Local Ontology"]
    M3 --> U0
    C1 --> U0
    U0 --> A["Central AICTE Ontology"]
    U1["University 1\nlocal/university1/university1.ttl"] --> A
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

0. Optionally convert a college relational database into RDF through the R2O layer.
   That layer now supports manual mapping, direct raw RDF export, and assisted refinement over the raw triples.
0A. Alternatively, accept a college-supplied OWL file plus mapping rules and run the same reasoning/query/validation pipeline on that package.
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
- `semantic/onboarding/` holds bring-your-own-OWL onboarding packages.
- `semantic/r2o/` holds the new relational-to-ontology onboarding example.
- `semantic/queries/core/`, `analysis/`, and `identity/` group the SPARQL files by purpose.
