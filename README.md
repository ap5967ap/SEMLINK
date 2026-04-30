# SEMLINK 🚀

**SEMLINK** is an AI-powered semantic integration framework that makes every database speak the same language. 

It leverages **Google Gemini** to automatically map heterogeneous university data models into a central **AICTE-aligned Knowledge Graph**, enabling unified querying and natural language interrogation across the entire institution.

---

## ⚡ Quick Start: Product Experience

To experience the full SEMLINK dashboard and AI onboarding:

### 1. Start the Backend API
```bash
export $(cat .env | xargs) && JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn spring-boot:run
```
*The API will be available at `http://localhost:8080`*

### 2. Start the Frontend Console
```bash
cd semlink-ui
npm install
npm run dev
```
*Open `http://localhost:5173` to browse the graph, run queries, and onboard new universities.*

---

## 🤖 AI-Powered "Zero-Touch" Onboarding

Onboard a new university in seconds using the CLI:

1. **Automate**: AI authors the R2RML mapping from your SQL.
   ```bash
   export $(cat .env | xargs) && JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn exec:java -Dexec.args="r2o automate <uni_id>"
   ```
2. **Generate**: Convert SQL data into Semantic SPO Triples.
   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn exec:java -Dexec.args="r2o generate <uni_id> file mappings/<uni_id>/r2rml-mapping.ttl"
   ```
3. **Query**: Run standard SPARQL checks on the new integrated data.
   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn exec:java -Dexec.args="query cs_students_by_university"
   ```
4. **Natural Language**: Ask questions in plain English.
   ```bash
   export $(cat .env | xargs) && JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn exec:java -Dexec.args="nl Show all students from <uni_id> with GPA > 9"
   ```

---

## 🛠️ Main CLI Commands

| Command | Purpose |
| --- | --- |
| `demo` | Runs the full ontology merge, reasoning, and query flow. |
| `query <name>` | Executes a named SPARQL query (e.g., `all_students`). |
| `nl <question>` | Translates English questions to SPARQL via Gemini. |
| `r2o automate <id>` | **[AI]** Generates expert R2RML mappings from SQL schema. |
| `pipeline run` | Runs the full pipeline and generates a dark HTML report. |
| `validate` | Performs SHACL quality validation on the graph. |
| `report` | Generates the standalone dark HTML presentation UI. |

---

## 📂 Project Structure (Core)
- `src/main/resources/semantic/` — Ontologies, Rules, and Queries.
- `mappings/` — AI-generated R2RML mapping files.
- `semlink-ui/` — React + Vite product console.
- `target/semantic-output/` — Generated RDF, reports, and query results.

---

> **Note:** Ensure your `GEMINI_API_KEY` is set in the `.env` file for all AI features.
