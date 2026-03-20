# SEMLINK

Semantic integration of heterogeneous university databases using OWL, RDF, SPARQL, SHACL, and a central AICTE ontology.

## Run

```bash
mvn -q exec:java -Dexec.args="demo"
```

Useful commands:

```bash
mvn -q exec:java -Dexec.args="query all_students"
mvn -q exec:java -Dexec.args="validate"
```

Generated outputs are written to `target/semantic-output/`.

Documentation is available in `docs/semantic-integration/`.

## Project Structure

```text
src/main/java/com/semlink/
src/main/resources/semantic/
  ontologies/
    central/
    local/
    support/
  queries/
    core/
    analysis/
    identity/
  rules/
  shapes/
docs/semantic-integration/
```
