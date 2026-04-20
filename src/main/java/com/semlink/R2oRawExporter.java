package com.semlink;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class R2oRawExporter {
    private static final String RAW_NS = "https://semlink.example.org/r2o/raw#";
    private static final String RAW_INSTANCE_NS = "https://semlink.example.org/r2o/raw/";

    public ExportResult export(SqlInputParser.SqlSchema schema, SqlInputParser.SqlData data) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("raw", RAW_NS);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("rdfs", RDFS.uri);

        Map<String, String> primaryKeys = new LinkedHashMap<>();
        for (SqlInputParser.TableDefinition table : schema.tables().values()) {
            String primaryKey = guessPrimaryKey(table);
            primaryKeys.put(table.name(), primaryKey);

            Resource tableClass = model.createResource(RAW_NS + localName(table.name()));
            model.add(tableClass, RDF.type, RDFS.Class);
            model.add(tableClass, RDFS.label, labelFor(table.name()));
        }

        for (SqlInputParser.TableDefinition table : schema.tables().values()) {
            Resource tableClass = model.createResource(RAW_NS + localName(table.name()));
            String primaryKey = primaryKeys.get(table.name());
            List<Map<String, Object>> rows = data.rows(table.name());
            for (Map<String, Object> row : rows) {
                Resource subject = model.createResource(rowUri(table.name(), primaryKey, row));
                model.add(subject, RDF.type, tableClass);

                for (String column : table.columns()) {
                    Object value = row.get(column);
                    if (value == null) {
                        continue;
                    }

                    Property columnProperty = model.createProperty(RAW_NS + localName(column));
                    model.add(subject, columnProperty, literal(model, value));

                    SqlInputParser.ForeignKey foreignKey = table.foreignKeys().get(column);
                    if (foreignKey != null) {
                        String targetPrimaryKey = primaryKeys.get(foreignKey.targetTable());
                        Resource target = model.createResource(rowUri(
                            foreignKey.targetTable(),
                            targetPrimaryKey,
                            Map.of(targetPrimaryKey, value)
                        ));
                        Property relationProperty = model.createProperty(RAW_NS + "ref" + capitalize(localName(column)));
                        model.add(subject, relationProperty, target);
                    }
                }
            }
        }

        return new ExportResult(model, primaryKeys);
    }

    public String rawNamespace() {
        return RAW_NS;
    }

    private String guessPrimaryKey(SqlInputParser.TableDefinition table) {
        for (String column : table.columns()) {
            if (column.toLowerCase(Locale.ROOT).endsWith("_id")) {
                return column;
            }
        }
        return table.columns().isEmpty() ? "row_id" : table.columns().getFirst();
    }

    private String rowUri(String tableName, String primaryKey, Map<String, Object> row) {
        Object idValue = row.get(primaryKey);
        String identifier = idValue == null ? "unknown" : sanitize(String.valueOf(idValue));
        return RAW_INSTANCE_NS + sanitize(tableName) + "/" + identifier;
    }

    private Literal literal(Model model, Object value) {
        if (value instanceof Integer integerValue) {
            return model.createTypedLiteral(integerValue);
        }
        if (value instanceof Double doubleValue) {
            return model.createTypedLiteral(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return model.createTypedLiteral(floatValue);
        }
        if (value instanceof Long longValue) {
            return model.createTypedLiteral(longValue);
        }
        if (value instanceof Boolean booleanValue) {
            return model.createTypedLiteral(booleanValue);
        }
        return model.createTypedLiteral(String.valueOf(value), XSDDatatype.XSDstring);
    }

    private String labelFor(String name) {
        return name.replace('_', ' ');
    }

    private String localName(String value) {
        String[] parts = value.split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(capitalize(part.toLowerCase(Locale.ROOT)));
        }
        if (builder.isEmpty()) {
            return "Value";
        }
        String localName = builder.toString();
        return Character.toLowerCase(localName.charAt(0)) + localName.substring(1);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    public record ExportResult(Model model, Map<String, String> primaryKeys) {
    }
}
