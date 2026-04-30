package com.semlink;

import java.nio.file.Path;
import java.util.List;

/**
 * Summary returned by the multi-source framework pipeline and public SDK.
 */
public record PipelineResult(
    List<SourcePipelineResult> sources,
    long mergedTripleCount,
    Path mergedModelPath,
    Path outputDirectory
) {
    public PipelineResult {
        sources = List.copyOf(sources == null ? List.of() : sources);
    }

    public int getSourceCount() {
        return sources.size();
    }

    public long getMergedModelSize() {
        return mergedTripleCount;
    }

    public int getQualityScore() {
        if (sources.isEmpty()) {
            return 0;
        }
        long conforming = sources.stream().filter(SourcePipelineResult::validationConforms).count();
        return Math.toIntExact(Math.round((conforming * 100.0) / sources.size()));
    }
}
