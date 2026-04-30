package com.semlink;

import java.nio.file.Path;

/**
 * Public SDK wrapper for SHACL shape resources.
 */
public record ShaclShapes(String location) {
    public static ShaclShapes from(String location) {
        return new ShaclShapes(location);
    }

    public Path asPath() {
        return Path.of(location);
    }
}
