package com.semlink;

import org.apache.jena.rdf.model.Model;

/**
 * Neo4j schema adapter for Cypher schema snippets. A production adapter can use
 * the Bolt driver to fetch labels, relationship types, and constraints.
 */
public class Neo4jAdapter implements DatabaseAdapter {
    private final ConnectionConfig config;
    private final Neo4jInputParser parser = new Neo4jInputParser();

    public Neo4jAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return "neo4j";
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.GRAPH;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        return parser.describe(config.id(), config.option("schema").orElse(""));
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        return parser.toRdf(config.id(), config.option("schema").orElse(""));
    }
}
