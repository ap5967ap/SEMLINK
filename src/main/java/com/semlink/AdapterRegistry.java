package com.semlink;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * Registry for built-in and custom database adapters. The shape mirrors the
 * future ServiceLoader plugin architecture while staying simple for the course
 * deliverable and CLI demo.
 */
public class AdapterRegistry {
    private final Map<String, Function<ConnectionConfig, DatabaseAdapter>> factories = new LinkedHashMap<>();

    public static AdapterRegistry withDefaults() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register("mysql", RelationalAdapter::new);
        registry.register("postgresql", RelationalAdapter::new);
        registry.register("oracle", RelationalAdapter::new);
        registry.register("sqlite", RelationalAdapter::new);
        registry.register("mongodb", MongoAdapter::new);
        registry.register("neo4j", Neo4jAdapter::new);
        registry.register("redis", RedisAdapter::new);
        registry.register("cassandra", CassandraAdapter::new);
        registry.register("owl", OwlFileAdapter::new);
        registry.register("ttl", OwlFileAdapter::new);
        registry.register("csv", CsvAdapter::new);
        ServiceLoader.load(DatabaseAdapterProvider.class)
            .forEach(provider -> registry.register(provider.type(), provider::create));
        return registry;
    }

    public void register(String type, Function<ConnectionConfig, DatabaseAdapter> factory) {
        factories.put(normalize(type), Objects.requireNonNull(factory, "factory"));
    }

    public DatabaseAdapter create(ConnectionConfig config) {
        Function<ConnectionConfig, DatabaseAdapter> factory = factories.get(normalize(config.type()));
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported adapter type: " + config.type() + ". Supported: " + supportedTypes());
        }
        return factory.apply(config);
    }

    public List<String> supportedTypes() {
        return List.copyOf(factories.keySet());
    }

    private static String normalize(String type) {
        return type.toLowerCase(Locale.ROOT);
    }
}
