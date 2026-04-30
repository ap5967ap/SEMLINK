package com.semlink;

import org.apache.jena.rdf.model.Model;

/**
 * Redis adapter for exported key/value JSON. It models keys as source records
 * and delegates semantic promotion to the assisted alignment layer.
 */
public class RedisAdapter implements DatabaseAdapter {
    private final ConnectionConfig config;
    private final KVInputParser parser = new KVInputParser();

    public RedisAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return "redis";
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.KV;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        return parser.describeRedis(config.id(), config.option("export").orElse(""));
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        return parser.redisToRdf(config.id(), config.option("export").orElse(""));
    }
}
