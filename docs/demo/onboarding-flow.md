# Onboarding Flow — LM-assisted R2O Demo

This document walks through the end-to-end R2O onboarding flow implemented in this repository. It demonstrates how relational data is processed, mapped to the AICTE ontology using a mix of LLM-assisted guidance and human review, transformed to RDF, validated, and published into a unified semantic graph with provenance.

Table of Contents
- Overview
- Prerequisites
- Demo data (schema + data)
- End-to-end steps (with sample commands)
- Expected outputs and provenance
- Flow diagrams
- Quick-start script

Overview
- The onboarding flow is designed to be end-to-end and interactive. You upload a relational database schema (SQL) and data, the system parses the schema, suggests mappings to the AICTE ontology via a Gemini-backed LM, presents mapping options with confidence, allows you to approve or modify mappings, and then generates RDF triples (AICTE-grounded). The flow is validated with SHACL shapes, and the final RDF is published into the central graph, with provenance kept in the output.
- If Gemini LM is unavailable or API key is not provided, the system falls back to heuristic-based mappings.
- The UI presents a 6-step wizard: Upload → Parse/LLM Suggest → Review → Transform → Validate → Publish. A lineage view updates upon publish.

Prerequisites
- Java 21, Maven, Node.js, and a running backend/frontend setup.
- Gemini API key may be configured via env var GEMINI_API_KEY; if not present, deterministic mappings are used.
- The demo uses simple test SQL files and existing backend endpoints.

Demo data
- You can reuse the demo SQL files shipped with the repository (see root: demo_uni5_schema.sql and demo_uni5_data.sql) or load your own.
- Ensure the SQL uses the same naming conventions used in the code base for compatibility with the parser.

End-to-end steps (with sample commands)
1) Start backend/frontend in development mode (or use your existing dev environment).
2) Prepare demo SQL files (schema + data) and use a demo onboarding name, e.g., demo-onboard.
3) Run onboarding (single-step) against the demo data:

```
curl -X POST http://localhost:8080/api/v1/onboard/sql \
  -H "Content-Type: application/json" \
  -d '{"name":"demo-onboard","schemaSql":"<contents of demo_uni5_schema.sql>","dataSql":"<contents of demo_uni5_data.sql>"}'
```

Response example (simplified):
- status: completed
- tables: number-of-tables
- rows: total-rows
- triples: number-of-triples-exported
- mergedIntoGraph: true/false
- newTotalTriples: total-triples-after-merge (if merged)
- extractedClasses: [...] (up to 20)

4) Inspect output in target/semantic-output/r2o/demo-onboard/:
- raw-mapping.ttl (the raw RDF)
- last-run-summary.txt
- (optional) merged.ttl if a full pipeline run is integrated
- mapping-suggestions.tsv (if LM was used and suggestions reported)

5) Validate, publish and verify lineage
- If the response indicates mergedIntoGraph, review the Lineage tab in the frontend to see the new node and edges.
- You can run ad-hoc SPARQL or NL queries via the Query Studio UI to verify the data.

Flow diagrams
```mermaid
graph TD;
  UPLOAD([Upload Schema & Data SQL]) --> PARSE[Parse SQL & LM Context]
  PARSE --> SUGGEST{LM Suggestion}
  SUGGEST --> REVIEW[Review & Edit Mappings]
  REVIEW --> TRANSFORM[Transform to AICTE RDF]
  TRANSFORM --> VALIDATE[SHACL Validation]
  VALIDATE --> PUBLISH[Publish to Unified Graph]
  PUBLISH --> LINEAGE[Lineage & Provenance Update]
  LINEAGE --> QUERIES[Query Studio (SPARQL/NL)]
```

Run Demo Script
- A small helper script is provided to run the end-to-end onboarding with the demo data. See docs/demo/run_demo.sh for details.

Quick-start script
- The repository includes a run-demo script that orchestrates onboarding from files to publish. See docs/demo/run_demo.sh for usage.
