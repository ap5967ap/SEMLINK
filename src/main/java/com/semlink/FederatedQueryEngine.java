package com.semlink;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * First-step federation engine for SEMLINK v1.5. It can merge RDF exports from
 * multiple adapters in memory today, while exposing SERVICE endpoint metadata
 * for the future live SPARQL federation mode.
 */
public class FederatedQueryEngine {
    public FederatedQueryPlan plan(String sparql, List<DatabaseAdapter> adapters) {
        List<String> endpoints = new ArrayList<>();
        StringBuilder services = new StringBuilder();
        StringBuilder provenance = new StringBuilder();
        String body = extractWhereBody(sparql);
        for (DatabaseAdapter adapter : adapters) {
            if (!adapter.supportsLiveQuery()) {
                continue;
            }
            String endpoint = adapter.toSPARQLServiceEndpoint();
            endpoints.add(endpoint);
            services.append("  SERVICE <").append(endpoint).append("> {\n")
                .append(indent(body)).append("\n")
                .append("  }\n");
            provenance.append(adapter.getConnectionConfig().id()).append(" -> ").append(endpoint).append('\n');
        }
        String serviceQuery = prefixes(sparql)
            + "SELECT * WHERE {\n"
            + services
            + "}";
        return new FederatedQueryPlan(sparql, serviceQuery, endpoints, provenance.toString());
    }

    public Model mergeExports(List<DatabaseAdapter> adapters, MappingRules mappingRules) {
        Model merged = ModelFactory.createDefaultModel();
        for (DatabaseAdapter adapter : adapters) {
            merged.add(adapter.exportToRDF(mappingRules));
        }
        return merged;
    }

    public String run(String sparql, List<DatabaseAdapter> adapters) {
        Model merged = mergeExports(adapters, MappingRules.assisted());
        Query query = QueryFactory.create(sparql);
        try (QueryExecution execution = QueryExecutionFactory.create(query, merged)) {
            if (query.isSelectType()) {
                return ResultSetFormatter.asText(execution.execSelect(), query);
            }
            if (query.isAskType()) {
                return Boolean.toString(execution.execAsk());
            }
            throw new IllegalArgumentException("FederatedQueryEngine supports SELECT and ASK queries in v1.5.");
        }
    }

    public String servicePlan(List<DatabaseAdapter> adapters) {
        StringBuilder plan = new StringBuilder();
        for (DatabaseAdapter adapter : adapters) {
            if (adapter.supportsLiveQuery()) {
                plan.append("SERVICE <").append(adapter.toSPARQLServiceEndpoint()).append("> # ")
                    .append(adapter.getConnectionConfig().id()).append('\n');
            }
        }
        return plan.toString();
    }

    private String prefixes(String sparql) {
        StringBuilder prefixes = new StringBuilder();
        for (String line : sparql.split("\\R")) {
            if (line.trim().toUpperCase().startsWith("PREFIX ")) {
                prefixes.append(line).append('\n');
            }
        }
        if (prefixes.isEmpty()) {
            prefixes.append("PREFIX aicte: <https://semlink.example.org/aicte#>\n");
        }
        return prefixes.toString();
    }

    private String extractWhereBody(String sparql) {
        int open = sparql.indexOf('{');
        int close = sparql.lastIndexOf('}');
        if (open >= 0 && close > open) {
            return sparql.substring(open + 1, close).trim();
        }
        return "?s ?p ?o .";
    }

    private String indent(String body) {
        StringBuilder indented = new StringBuilder();
        for (String line : body.split("\\R")) {
            indented.append("    ").append(line).append('\n');
        }
        return indented.toString().stripTrailing();
    }
}
