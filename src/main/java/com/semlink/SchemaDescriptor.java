package com.semlink;

import java.util.List;
import java.util.Objects;

/**
 * Adapter-neutral schema summary that can be compared, mapped, and rendered
 * without depending on the source database driver's object model.
 */
public record SchemaDescriptor(
    String sourceId,
    DataModelType dataModelType,
    List<String> entities,
    List<String> attributes,
    List<String> relationships
) {
    public SchemaDescriptor {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(dataModelType, "dataModelType");
        entities = List.copyOf(entities == null ? List.of() : entities);
        attributes = List.copyOf(attributes == null ? List.of() : attributes);
        relationships = List.copyOf(relationships == null ? List.of() : relationships);
    }

    public static SchemaDescriptor empty(String sourceId, DataModelType type) {
        return new SchemaDescriptor(sourceId, type, List.of(), List.of(), List.of());
    }
}
