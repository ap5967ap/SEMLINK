package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;

/**
 * Extension point for turning a native data source into the SEMLINK semantic
 * pipeline. Adapters are intentionally small: discover schema, export RDF,
 * report whether live SPARQL federation is available, and expose provenance.
 */
public interface DatabaseAdapter {
    String getType();

    DataModelType getDataModelType();

    ConnectionConfig getConnectionConfig();

    SchemaDescriptor extractSchema();

    Model exportToRDF(MappingRules rules);

    default ValidationReport validate(Model rdf) {
        Shapes emptyShapes = ShaclValidator.get().parse(ModelFactory.createDefaultModel().getGraph());
        return ShaclValidator.get().validate(emptyShapes, rdf.getGraph());
    }

    default boolean supportsLiveQuery() {
        return !toSPARQLServiceEndpoint().isBlank();
    }

    default String toSPARQLServiceEndpoint() {
        return getConnectionConfig().option("sparqlEndpoint").orElse("");
    }
}
