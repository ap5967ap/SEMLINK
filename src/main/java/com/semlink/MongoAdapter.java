package com.semlink;

import org.apache.jena.rdf.model.Model;

import java.util.List;

/**
 * Document adapter facade. The zero-dependency implementation consumes a sample
 * JSON shape from ConnectionConfig options; production wiring can replace this
 * with the MongoDB Java driver without changing the DatabaseAdapter contract.
 */
public class MongoAdapter implements DatabaseAdapter {
    private final ConnectionConfig config;
    private final MongoInputParser parser = new MongoInputParser();

    public MongoAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return "mongodb";
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.DOCUMENT;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        String collection = config.option("collection").orElse(config.database());
        String sample = config.option("sample").orElse("{}");
        return parser.describe(config.id(), collection, sample);
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        String collection = config.option("collection").orElse(config.database());
        String sample = config.option("sample").orElse("{}");
        return parser.toRdf(config.id(), collection, sample);
    }
}
