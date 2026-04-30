package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the existing SEMLINK behavior: local OWL, RDF/XML, Turtle, or
 * RDF resources are loaded directly into a Jena model.
 */
public class OwlFileAdapter implements DatabaseAdapter {
    private final ConnectionConfig config;

    public OwlFileAdapter(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public String getType() {
        return "owl";
    }

    @Override
    public DataModelType getDataModelType() {
        return DataModelType.ONTOLOGY_FILE;
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return config;
    }

    @Override
    public SchemaDescriptor extractSchema() {
        Model model = exportToRDF(MappingRules.assisted());
        List<String> classes = new ArrayList<>();
        ResIterator iterator = model.listResourcesWithProperty(RDF.type, OWL.Class);
        try {
            iterator.forEachRemaining(resource -> {
                if (resource.isURIResource()) {
                    classes.add(resource.getURI());
                }
            });
        } finally {
            iterator.close();
        }
        return new SchemaDescriptor(config.id(), DataModelType.ONTOLOGY_FILE, classes, List.of(), List.of());
    }

    @Override
    public Model exportToRDF(MappingRules rules) {
        String path = config.option("path").orElse(config.database());
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, path);
        return model;
    }
}
