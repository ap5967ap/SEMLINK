package com.semlink;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes whether source-to-ontology mapping is assisted by SEMLINK or backed
 * by a concrete mapping/rules file such as R2RML, YARRRML, or Jena rules.
 */
public record MappingRules(String mode, Optional<Path> path) {
    public MappingRules {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(path, "path");
    }

    public static MappingRules assisted() {
        return new MappingRules("assisted", Optional.empty());
    }

    public static MappingRules manual(Path path) {
        return new MappingRules("manual", Optional.of(path));
    }
}
