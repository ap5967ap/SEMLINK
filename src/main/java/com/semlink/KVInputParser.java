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
 * Parser for key-value exports and Cassandra CQL table definitions. The output
 * preserves physical modeling choices such as Redis key patterns and Cassandra
 * partition/clustering columns for later semantic alignment.
 */
public class KVInputParser {
    private static final String NS = "https://semlink.example.org/source/kv#";
    private static final Pattern REDIS_KEY_PATTERN = Pattern.compile("([A-Za-z0-9_:-]+)\\s*[:=]");
    private static final Pattern CQL_TABLE_PATTERN = Pattern.compile("(?i)CREATE\\s+TABLE\\s+([A-Za-z0-9_]+)");
    private static final Pattern CQL_COLUMN_PATTERN = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+(text|varchar|int|boolean|date|timestamp|uuid|double|float)", Pattern.CASE_INSENSITIVE);

    public SchemaDescriptor describeRedis(String sourceId, String export) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = REDIS_KEY_PATTERN.matcher(export == null ? "" : export);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return new SchemaDescriptor(sourceId, DataModelType.KV, new ArrayList<>(keys), List.of("jsonValue"), List.of());
    }

    public Model redisToRdf(String sourceId, String export) {
        SchemaDescriptor schema = describeRedis(sourceId, export);
        Model model = ModelFactory.createDefaultModel();
        schema.entities().forEach(key -> model.createResource(NS + sourceId + "/redis/" + key.replace(':', '/'))
            .addProperty(RDF.type, model.createResource(NS + "RedisKey"))
            .addProperty(model.createProperty(NS + "pattern"), key));
        return model;
    }

    public SchemaDescriptor describeCql(String sourceId, String cql) {
        String table = "cassandra_table";
        Matcher tableMatcher = CQL_TABLE_PATTERN.matcher(cql == null ? "" : cql);
        if (tableMatcher.find()) {
            table = tableMatcher.group(1);
        }
        List<String> columns = new ArrayList<>();
        Matcher columnMatcher = CQL_COLUMN_PATTERN.matcher(cql == null ? "" : cql);
        while (columnMatcher.find()) {
            columns.add(table + "." + columnMatcher.group(1));
        }
        return new SchemaDescriptor(sourceId, DataModelType.WIDE_COLUMN, List.of(table), columns, List.of("partitionedBy", "clusteredBy"));
    }

    public Model cqlToRdf(String sourceId, String cql) {
        SchemaDescriptor schema = describeCql(sourceId, cql);
        Model model = ModelFactory.createDefaultModel();
        schema.entities().forEach(table -> model.createResource(NS + sourceId + "/cassandra/" + table)
            .addProperty(RDF.type, model.createResource(NS + "WideColumnTable"))
            .addProperty(model.createProperty(NS + "name"), table));
        schema.attributes().forEach(column -> model.createResource(NS + sourceId + "/cassandra-column/" + column.replace('.', '/'))
            .addProperty(RDF.type, model.createResource(NS + "WideColumn"))
            .addProperty(model.createProperty(NS + "name"), column));
        return model;
    }
}
