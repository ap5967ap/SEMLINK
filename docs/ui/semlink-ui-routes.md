# SEMLINK React UI

The runnable product console lives in `semlink-ui/`. It is a React 19 + Vite
single-page app for presenting SEMLINK as an AICTE semantic integration product.
The static dark report at `target/semantic-output/index.html` remains available
as a no-server fallback.

## Run

```bash
cd semlink-ui
npm install
npm run dev -- --port 5173
```

Open:

```text
http://127.0.0.1:5173
```

Verify before presenting:

```bash
cd semlink-ui
npm test
npm run build
```

## Visual System

- Theme: dark regulator operations console.
- Background: `#080b12`.
- Surface: `#101725`.
- Accent: `#4fd1c5`.
- Cards only for repeated source/status items.
- Primary workflow: Connect -> Map -> Align -> Validate -> Query.

## Routes

| Route | Purpose |
| --- | --- |
| `/dashboard` | Source count, triple count, quality score, last sync, pipeline status, source health. |
| `/connections` | Add connection configuration, review adapter coverage, and stage new sources. |
| `/explore` | Browse source schemas, ontology classes, properties, instances, and identity clusters. |
| `/query` | SPARQL playground, query catalog, NL query bar, run button, provenance result table. |
| `/validate` | SHACL report, severity list, quality scores, and regulator-ready violations. |
| `/onboard` | University5 SQL/R2O onboarding with Connect -> Discover -> Map -> Validate -> Publish. |
| `/lineage` | Source-to-result provenance graph for AICTE rows. |
| `/use-cases` | Five runnable demo scenarios with before query, after SPARQL, wow moment, and lesson. |

## Presentation Flow

1. Start at `/dashboard` and explain the central AICTE semantic layer.
2. Open `/connections` to show the adapter framework from Block 2.
3. Open `/onboard` to show the University5 self-service onboarding story.
4. Open `/query`, click `Run SPARQL`, and show one query returning results from four sources.
5. Open `/validate` and explain SHACL quality scoring.
6. Open `/lineage` to prove each row keeps source provenance.
7. Close on `/use-cases` to show AICTE, deduplication, quality, NL query, and onboarding demos.

## Demo-Ready Static Output Fallback

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="report"
```

Open:

```text
target/semantic-output/index.html
```
