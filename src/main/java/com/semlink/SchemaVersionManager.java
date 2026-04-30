package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares RDF schema snapshots and records version history for schema drift
 * analysis. Additions are compatible; removals or renames are treated as
 * breaking because existing R2RML/Jena mappings may stop resolving.
 */
public class SchemaVersionManager {
    public OntologyDiff diff(String sourceId, Model previous, Model current) {
        Set<String> previousTriples = triples(previous);
        Set<String> currentTriples = triples(current);
        Set<String> added = new LinkedHashSet<>(currentTriples);
        added.removeAll(previousTriples);
        Set<String> removed = new LinkedHashSet<>(previousTriples);
        removed.removeAll(currentTriples);

        SchemaChangeType type = SchemaChangeType.COMPATIBLE;
        if (!removed.isEmpty()) {
            type = SchemaChangeType.BREAKING;
        } else if (!added.isEmpty()) {
            type = SchemaChangeType.ADDITIVE;
        }
        return new OntologyDiff(
            sourceId,
            type,
            added.size(),
            removed.size(),
            added.stream().limit(10).toList(),
            removed.stream().limit(10).toList()
        );
    }

    public void writeVersionHistory(Path output, OntologyDiff diff) {
        String json = "{\n"
            + "  \"timestamp\": \"" + Instant.now() + "\",\n"
            + "  \"sourceId\": \"" + escape(diff.sourceId()) + "\",\n"
            + "  \"changeType\": \"" + diff.changeType() + "\",\n"
            + "  \"addedTriples\": " + diff.addedTriples() + ",\n"
            + "  \"removedTriples\": " + diff.removedTriples() + "\n"
            + "}\n";
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, json, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write schema version history to " + output, exception);
        }
    }

    private Set<String> triples(Model model) {
        Set<String> triples = new LinkedHashSet<>();
        model.listStatements().forEachRemaining(statement -> triples.add(format(statement)));
        return triples;
    }

    private String format(Statement statement) {
        return statement.getSubject() + " " + statement.getPredicate() + " " + statement.getObject();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
