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
    private static final Map<String, String> QUERY_RESOURCES = new LinkedHashMap<>();

    static {
        QUERY_RESOURCES.put("all_students", "semantic/queries/core/all_students.rq");
        QUERY_RESOURCES.put("students_in_computer_science", "semantic/queries/core/students_in_computer_science.rq");
        QUERY_RESOURCES.put("colleges_by_university", "semantic/queries/core/colleges_by_university.rq");
        QUERY_RESOURCES.put("courses_by_college", "semantic/queries/core/courses_by_college.rq");
        QUERY_RESOURCES.put("student_college_resolution", "semantic/queries/core/student_college_resolution.rq");
        QUERY_RESOURCES.put("student_count_by_university", "semantic/queries/analysis/student_count_by_university.rq");
        QUERY_RESOURCES.put("student_count_by_department", "semantic/queries/analysis/student_count_by_department.rq");
        QUERY_RESOURCES.put("cs_students_by_university", "semantic/queries/analysis/cs_students_by_university.rq");
        QUERY_RESOURCES.put("course_count_by_college", "semantic/queries/analysis/course_count_by_college.rq");
        QUERY_RESOURCES.put("department_to_college_map", "semantic/queries/analysis/department_to_college_map.rq");
        QUERY_RESOURCES.put("same_as_clusters", "semantic/queries/identity/same_as_clusters.rq");
        QUERY_RESOURCES.put("same_as_student_details", "semantic/queries/identity/same_as_student_details.rq");
    }

    public List<String> listQueries() {
        return List.copyOf(QUERY_RESOURCES.keySet());
    }

    public Map<String, String> runAll(Model model) {
        Map<String, String> outputs = new LinkedHashMap<>();
        for (String queryName : QUERY_RESOURCES.keySet()) {
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
        String resource = QUERY_RESOURCES.get(queryName);
        if (resource == null) {
            throw new IllegalArgumentException("Unknown query: " + queryName + ". Available queries: " + QUERY_RESOURCES.keySet());
        }
        return resource;
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
