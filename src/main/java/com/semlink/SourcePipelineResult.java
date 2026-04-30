package com.semlink;

import java.nio.file.Path;

/**
 * Result of ingesting one configured source through a DatabaseAdapter.
 */
public record SourcePipelineResult(
    String sourceId,
    DataModelType dataModelType,
    int entityCount,
    int attributeCount,
    long tripleCount,
    boolean validationConforms,
    Path outputDirectory
) {
}
