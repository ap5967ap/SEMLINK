package com.semlink;

import org.apache.jena.rdf.model.Model;

/**
 * Cassandra adapter for CQL schema snippets. It captures wide-column table,
 * partition-key, and clustering-key information for mapping review.
 */
public class CassandraAdapter implements DatabaseAdapter {
    private final ConnectionConfig config;
    private final KVInputParser parser = new KVInputParser();

    public CassandraAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return "cassandra";
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.WIDE_COLUMN;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        return parser.describeCql(config.id(), config.option("cql").orElse(""));
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        return parser.cqlToRdf(config.id(), config.option("cql").orElse(""));
    }
}
