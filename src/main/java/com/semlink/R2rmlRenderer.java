package com.semlink;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class R2rmlRenderer {
    private static final String RR_NS = "http://www.w3.org/ns/r2rml#";
    private static final Resource TRIPLES_MAP = resource(RR_NS + "TriplesMap");
    private static final Property LOGICAL_TABLE = property(RR_NS + "logicalTable");
    private static final Property TABLE_NAME = property(RR_NS + "tableName");
    private static final Property SUBJECT_MAP = property(RR_NS + "subjectMap");
    private static final Property TEMPLATE = property(RR_NS + "template");
    private static final Property CLASS = property(RR_NS + "class");
    private static final Property PREDICATE_OBJECT_MAP = property(RR_NS + "predicateObjectMap");
    private static final Property PREDICATE = property(RR_NS + "predicate");
    private static final Property OBJECT_MAP = property(RR_NS + "objectMap");
    private static final Property COLUMN = property(RR_NS + "column");

    private final SqlInputParser sqlInputParser = new SqlInputParser();

    public RenderResult render(String mappingTurtle, String dataSql) {
        Model mappingModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(mappingModel, new StringReader(mappingTurtle), null, org.apache.jena.riot.Lang.TURTLE);

        SqlInputParser.SqlData sqlData = sqlInputParser.parseData(dataSql);
        Model output = ModelFactory.createDefaultModel();

        List<TriplesMapDefinition> triplesMaps = readTriplesMaps(mappingModel);
        for (TriplesMapDefinition triplesMap : triplesMaps) {
            for (Map<String, Object> row : sqlData.rows(triplesMap.tableName())) {
                String subjectUri = applyTemplate(triplesMap.subjectTemplate(), row);
                if (subjectUri == null) {
                    continue;
                }
                Resource subject = output.createResource(subjectUri);
                if (triplesMap.classUri() != null) {
                    output.add(subject, RDF.type, output.createResource(triplesMap.classUri()));
                }
                for (PredicateObjectDefinition predicateObject : triplesMap.predicateObjects()) {
                    RDFNode objectNode = buildObject(output, predicateObject, row);
                    if (objectNode != null) {
                        output.add(subject, output.createProperty(predicateObject.predicateUri()), objectNode);
                    }
                }
            }
        }

        return new RenderResult(output, triplesMaps.size(), countRows(sqlData));
    }

    private List<TriplesMapDefinition> readTriplesMaps(Model mappingModel) {
        List<TriplesMapDefinition> definitions = new ArrayList<>();
        mappingModel.listResourcesWithProperty(RDF.type, TRIPLES_MAP).forEachRemaining(triplesMap -> {
            Resource logicalTable = triplesMap.getPropertyResourceValue(LOGICAL_TABLE);
            Resource subjectMap = triplesMap.getPropertyResourceValue(SUBJECT_MAP);
            if (logicalTable == null || subjectMap == null) {
                return;
            }
            Statement tableNameStatement = logicalTable.getProperty(TABLE_NAME);
            Statement templateStatement = subjectMap.getProperty(TEMPLATE);
            if (tableNameStatement == null || templateStatement == null || !tableNameStatement.getObject().isLiteral()) {
                return;
            }

            String tableName = tableNameStatement.getString();
            String subjectTemplate = templateStatement.getString();
            Statement classStatement = subjectMap.getProperty(CLASS);
            String classUri = classStatement != null && classStatement.getObject().isResource()
                ? classStatement.getResource().getURI()
                : null;

            List<PredicateObjectDefinition> predicateObjects = new ArrayList<>();
            triplesMap.listProperties(PREDICATE_OBJECT_MAP).forEachRemaining(statement -> {
                Resource predicateObjectMap = statement.getResource();
                Statement predicateStatement = predicateObjectMap.getProperty(PREDICATE);
                Resource objectMap = predicateObjectMap.getPropertyResourceValue(OBJECT_MAP);
                if (predicateStatement == null || objectMap == null || !predicateStatement.getObject().isResource()) {
                    return;
                }
                String predicateUri = predicateStatement.getResource().getURI();
                String column = literalValue(objectMap.getProperty(COLUMN));
                String template = literalValue(objectMap.getProperty(TEMPLATE));
                predicateObjects.add(new PredicateObjectDefinition(predicateUri, column, template));
            });

            definitions.add(new TriplesMapDefinition(tableName, subjectTemplate, classUri, predicateObjects));
        });
        return definitions;
    }

    private RDFNode buildObject(Model output, PredicateObjectDefinition definition, Map<String, Object> row) {
        if (definition.column() != null) {
            Object value = row.get(definition.column());
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                Literal typedLiteral = output.createTypedLiteral(number);
                return typedLiteral;
            }
            return output.createLiteral(value.toString());
        }
        if (definition.template() != null) {
            String objectUri = applyTemplate(definition.template(), row);
            if (objectUri == null) {
                return null;
            }
            return output.createResource(objectUri);
        }
        return null;
    }

    private String applyTemplate(String template, Map<String, Object> row) {
        String resolved = template;
        int start = resolved.indexOf('{');
        while (start >= 0) {
            int end = resolved.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String key = resolved.substring(start + 1, end);
            Object value = row.get(key);
            if (value == null) {
                return null;
            }
            resolved = resolved.substring(0, start) + value + resolved.substring(end + 1);
            start = resolved.indexOf('{', start + String.valueOf(value).length());
        }
        return resolved;
    }

    private int countRows(SqlInputParser.SqlData data) {
        return data.rowsByTable().values().stream()
            .mapToInt(List::size)
            .sum();
    }

    private String literalValue(Statement statement) {
        if (statement == null) {
            return null;
        }
        RDFNode node = statement.getObject();
        return node.isLiteral() ? node.asLiteral().getString() : null;
    }

    private static Resource resource(String uri) {
        return ModelFactory.createDefaultModel().createResource(uri);
    }

    private static Property property(String uri) {
        return ModelFactory.createDefaultModel().createProperty(uri);
    }

    public record RenderResult(Model model, int triplesMapCount, int inputRowCount) {
        public int tripleCount() {
            return Math.toIntExact(model.size());
        }
    }

    private record TriplesMapDefinition(
        String tableName,
        String subjectTemplate,
        String classUri,
        List<PredicateObjectDefinition> predicateObjects
    ) {
    }

    private record PredicateObjectDefinition(String predicateUri, String column, String template) {
    }
}
