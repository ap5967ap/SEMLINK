package com.semlink;

import java.util.List;

/**
 * Explainable plan for a SPARQL federation query.
 */
public record FederatedQueryPlan(
    String originalQuery,
    String serviceQuery,
    List<String> serviceEndpoints,
    String provenance
) {
    public FederatedQueryPlan {
        serviceEndpoints = List.copyOf(serviceEndpoints == null ? List.of() : serviceEndpoints);
    }
}
