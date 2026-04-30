package com.semlink;

/**
 * ServiceLoader hook for adapter plugins distributed outside the core project.
 */
public interface DatabaseAdapterProvider {
    String type();

    DatabaseAdapter create(ConnectionConfig config);
}
