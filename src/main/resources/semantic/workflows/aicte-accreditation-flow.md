# AICTE Accreditation Workflow

## Purpose

Show how SEMLINK unifies four heterogeneous university models under the AICTE ontology for accreditation reporting.

## Sources

| Source | Data model | SEMLINK artifact |
| --- | --- | --- |
| University1 | Relational-style ontology and R2O sample | `ontologies/local/university1/university1.ttl`, `r2o/example-college/` |
| University2 | Document-style local vocabulary | `ontologies/local/university2/university2.ttl` |
| University3 | Graph-style local vocabulary | `ontologies/local/university3/university3.ttl` |
| University4 | KV/indirect relationship vocabulary | `ontologies/local/university4/university4.ttl` |

## Presenter Flow

1. Run `demo` to generate merged, inferred, query, validation, and mapping outputs.
2. Run `query cs_students_by_university` to show one AICTE query across four sources.
3. Run `query same_as_student_details` to show cross-source identity resolution.
4. Run `schema diff` to show ontology versioning and additive drift classification.
5. Run `report` to open the dark UI report.

## Wow Query

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

## Data Modelling Lesson

This workflow demonstrates ER/EER to relational mapping, ontology alignment, RDF graph materialization, `owl:sameAs` identity resolution, SHACL validation, and federated-style SPARQL querying over heterogeneous models.
