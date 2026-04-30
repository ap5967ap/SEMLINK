# SEMLINK Future Feature Plan

## Near Term

### Semantic Cache

Cache SPARQL results by query hash, ontology version, source version, and policy context. Invalidate when schema drift or source freshness changes.

### Data Lineage Graph

Represent provenance with PROV-O triples:

```turtle
@prefix prov: <http://www.w3.org/ns/prov#> .
@prefix semlink: <https://semlink.example.org/prov#> .

semlink:answerRow123 prov:wasDerivedFrom semlink:university1_tbl_students_R202301 ;
  prov:wasGeneratedBy semlink:cs_students_by_university_query .
```

### Query Cost Estimator

Estimate source hits, cardinality, latency, and missing indexes before execution. Use adapter capabilities plus historical query logs.

### Schema Suggestion Engine

When a new source appears, SEMLINK suggests mappings such as `learner_id -> aicte:id`, `full_nm -> aicte:name`, and `brn_cd -> aicte:department`.

### Ontology Diff Viewer

Render added triples, removed triples, and changed SHACL shapes in a visual diff.

## Medium Term

- Multi-domain ontology hub.
- Live SPARQL `SERVICE` federation through embedded Fuseki.
- Collaborative SPARQL workspace.
- GraphQL API generated from OWL.

## Long Term

- Hosted `semlink.cloud`.
- Ontology marketplace.
- Adapter marketplace.
- Time-travel semantic queries over TDB2 snapshots.
