package com.semlink;

/**
 * Public SDK reasoner choices. The current kernel uses Jena rule inference;
 * these values document the intended framework contract.
 */
public enum ReasonerType {
    RDFS,
    OWL_MICRO,
    OWL_MINI,
    OWL_DL
}
