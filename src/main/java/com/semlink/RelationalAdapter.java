package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC adapter for relational sources. It uses DatabaseMetaData for zero-custom
 * introspection and exports schema-level RDF that can feed the assisted mapper.
 */
public class RelationalAdapter implements DatabaseAdapter {
    private static final String NS = "https://semlink.example.org/source/relational#";
    private final ConnectionConfig config;

    public RelationalAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return config.type();
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.RELATIONAL;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        List<String> tables = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> relationships = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tableRows = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tableRows.next()) {
                    String table = tableRows.getString("TABLE_NAME");
                    tables.add(table);
                    try (ResultSet columnRows = metadata.getColumns(null, null, table, "%")) {
                        while (columnRows.next()) {
                            columns.add(table + "." + columnRows.getString("COLUMN_NAME"));
                        }
                    }
                    try (ResultSet fkRows = metadata.getImportedKeys(null, null, table)) {
                        while (fkRows.next()) {
                            relationships.add(table + "." + fkRows.getString("FKCOLUMN_NAME") + " -> "
                                + fkRows.getString("PKTABLE_NAME") + "." + fkRows.getString("PKCOLUMN_NAME"));
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to inspect JDBC source " + config.id() + " at " + config.jdbcUrl(), exception);
        }
        return new SchemaDescriptor(config.id(), DataModelType.RELATIONAL, tables, columns, relationships);
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        SchemaDescriptor schema = extractSchema();
        Model model = ModelFactory.createDefaultModel();
        schema.entities().forEach(table -> model.createResource(NS + config.id() + "/table/" + table)
            .addProperty(RDF.type, model.createResource(NS + "Table"))
            .addProperty(model.createProperty(NS + "name"), table));
        schema.attributes().forEach(column -> model.createResource(NS + config.id() + "/column/" + column.replace('.', '/'))
            .addProperty(RDF.type, model.createResource(NS + "Column"))
            .addProperty(model.createProperty(NS + "name"), column));
        schema.relationships().forEach(relationship -> model.createResource(NS + config.id() + "/foreignKey/" + Math.abs(relationship.hashCode()))
            .addProperty(RDF.type, model.createResource(NS + "ForeignKey"))
            .addProperty(model.createProperty(NS + "expression"), relationship));
        return model;
    }
}
