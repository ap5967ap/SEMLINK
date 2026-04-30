package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * CSV adapter for quick demos and spreadsheet-like institutional uploads.
 */
public class CsvAdapter implements DatabaseAdapter {
    private static final String NS = "https://semlink.example.org/source/csv#";
    private final ConnectionConfig config;

    public CsvAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return "csv";
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.TABULAR;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        String path = config.option("path").orElse(config.database());
        try {
            String header = Files.readAllLines(Path.of(path)).stream().findFirst().orElse("");
            List<String> columns = Arrays.stream(header.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
            return new SchemaDescriptor(config.id(), DataModelType.TABULAR, List.of(Path.of(path).getFileName().toString()), columns, List.of());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read CSV source " + path, exception);
        }
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        SchemaDescriptor schema = extractSchema();
        Model model = ModelFactory.createDefaultModel();
        schema.attributes().forEach(column -> model.createResource(NS + config.id() + "/column/" + column)
            .addProperty(RDF.type, model.createResource(NS + "Column"))
            .addProperty(model.createProperty(NS + "name"), column));
        return model;
    }
}
