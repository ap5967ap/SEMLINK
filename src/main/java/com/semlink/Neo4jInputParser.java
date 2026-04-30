package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Neo4j labels and relationship types from Cypher DDL/query snippets and
 * represents them as RDF schema facts for alignment.
 */
public class Neo4jInputParser {
    private static final String NS = "https://semlink.example.org/source/property-graph#";
    private static final Pattern LABEL_PATTERN = Pattern.compile(":([A-Z][A-Za-z0-9_]*)");
    private static final Pattern REL_PATTERN = Pattern.compile("\\[:([A-Z_][A-Z0-9_]*)");

    public SchemaDescriptor describe(String sourceId, String cypher) {
        Set<String> labels = new LinkedHashSet<>();
        Set<String> relationships = new LinkedHashSet<>();
        Matcher labelMatcher = LABEL_PATTERN.matcher(cypher == null ? "" : cypher);
        while (labelMatcher.find()) {
            labels.add(labelMatcher.group(1));
        }
        Matcher relationshipMatcher = REL_PATTERN.matcher(cypher == null ? "" : cypher);
        while (relationshipMatcher.find()) {
            relationships.add(relationshipMatcher.group(1));
        }
        return new SchemaDescriptor(sourceId, DataModelType.GRAPH, new ArrayList<>(labels), java.util.List.of(), new ArrayList<>(relationships));
    }

    public Model toRdf(String sourceId, String cypher) {
        SchemaDescriptor schema = describe(sourceId, cypher);
        Model model = ModelFactory.createDefaultModel();
        schema.entities().forEach(label -> model.createResource(NS + sourceId + "/label/" + label)
            .addProperty(RDF.type, model.createResource(NS + "NodeLabel"))
            .addProperty(model.createProperty(NS + "name"), label));
        schema.relationships().forEach(type -> model.createResource(NS + sourceId + "/relationship/" + type)
            .addProperty(RDF.type, model.createResource(NS + "RelationshipType"))
            .addProperty(model.createProperty(NS + "name"), type));
        return model;
    }
}
