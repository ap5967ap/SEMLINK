package com.semlink;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QueryEngine {
    private static final List<String> QUERY_NAMES = List.of(
        "all_students",
        "students_in_computer_science",
        "colleges_by_university",
        "courses_by_college",
        "same_as_clusters",
        "student_college_resolution"
    );

    private final String queryRoot;

    public QueryEngine(String queryRoot) {
        this.queryRoot = queryRoot;
    }

    public List<String> listQueries() {
        return QUERY_NAMES;
    }

    public Map<String, String> runAll(Model model) {
        Map<String, String> outputs = new LinkedHashMap<>();
        for (String queryName : QUERY_NAMES) {
            outputs.put(queryName, run(queryName, model));
        }
        return outputs;
    }

    public String run(String queryName, Model model) {
        String queryString = readResource(queryResource(queryName));
        Query query = QueryFactory.create(queryString);

        try (QueryExecution execution = QueryExecutionFactory.create(query, model)) {
            if (query.isSelectType()) {
                return ResultSetFormatter.asText(execution.execSelect(), query);
            }
            if (query.isAskType()) {
                return Boolean.toString(execution.execAsk());
            }
            throw new IllegalArgumentException("Unsupported query type for " + queryName);
        }
    }

    private String queryResource(String queryName) {
        return queryRoot + "/" + queryName + ".rq";
    }

    private String readResource(String resourcePath) {
        try (InputStream inputStream = resourceStream(resourcePath)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read query resource " + resourcePath, exception);
        }
    }

    private InputStream resourceStream(String resourcePath) {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        return Objects.requireNonNull(stream, "Missing resource: " + resourcePath);
    }
}
