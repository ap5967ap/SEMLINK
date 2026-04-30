package com.semlink;

import java.util.List;

/**
 * Summary of changes between two ontology or source-schema RDF snapshots.
 */
public record OntologyDiff(
    String sourceId,
    SchemaChangeType changeType,
    int addedTriples,
    int removedTriples,
    List<String> addedExamples,
    List<String> removedExamples
) {
    public OntologyDiff {
        addedExamples = List.copyOf(addedExamples == null ? List.of() : addedExamples);
        removedExamples = List.copyOf(removedExamples == null ? List.of() : removedExamples);
    }
}
