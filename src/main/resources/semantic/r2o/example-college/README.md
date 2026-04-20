# R2O Example College

This example shows the new optional Step 0 of the project:

1. A college keeps data in a relational database.
2. A direct R2O export turns tables, columns, and foreign keys into raw RDF triples.
3. The assistant refines those raw triples into AICTE-ready semantic data without asking the college to author ontology files.

Files:

- `schema.sql` defines the relational schema.
- `sample-data.sql` inserts sample rows.
- `r2rml-mapping.ttl` shows how tables and columns are mapped to AICTE concepts.
- `generated-aicte-ready.ttl` shows the RDF output expected from the mapping.

This example is intentionally kept separate from the main demo so the current ontology-first workflow remains intact.

Commands:

- `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o raw example-college"`
- `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o assist example-college"`
- `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q exec:java -Dexec.args="r2o generate example-college manual"`

Generated artifacts:

- `target/semantic-output/r2o/example-college/raw/`
- `target/semantic-output/r2o/example-college/assisted/`
- `target/semantic-output/r2o/example-college/generated/`
