package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight document-shape parser for MongoDB-style sample JSON. It extracts
 * field paths for schema mapping and emits source-faithful RDF triples.
 */
public class MongoInputParser {
    private static final String NS = "https://semlink.example.org/source/document#";
    private static final Pattern FIELD_PATTERN = Pattern.compile("\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*:");

    public SchemaDescriptor describe(String sourceId, String collection, String sampleJson) {
        return new SchemaDescriptor(sourceId, DataModelType.DOCUMENT, List.of(collection), new ArrayList<>(fieldNames(sampleJson)), List.of());
    }

    public Model toRdf(String sourceId, String collection, String sampleJson) {
        Model model = ModelFactory.createDefaultModel();
        model.createResource(NS + sourceId + "/collection/" + collection)
            .addProperty(RDF.type, model.createResource(NS + "Collection"))
            .addProperty(model.createProperty(NS + "name"), collection);
        for (String field : fieldNames(sampleJson)) {
            model.createResource(NS + sourceId + "/field/" + field)
                .addProperty(RDF.type, model.createResource(NS + "Field"))
                .addProperty(model.createProperty(NS + "name"), field)
                .addProperty(model.createProperty(NS + "collection"), collection);
        }
        return model;
    }

    private Set<String> fieldNames(String sampleJson) {
        Set<String> fields = new LinkedHashSet<>();
        Matcher matcher = FIELD_PATTERN.matcher(sampleJson == null ? "" : sampleJson);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }
}
