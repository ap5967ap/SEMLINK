# E-Commerce Semantic Workflow

## Purpose

Show how the SEMLINK architecture can answer customer intelligence questions across transactional, document, graph, and event stores.

## Sources

| Source | Native model | Example data |
| --- | --- | --- |
| PostgreSQL | Relational | Users, orders, addresses |
| MongoDB | Document | Product catalog and variants |
| Neo4j | Property graph | Product similarity and recommendations |
| Kafka or ClickHouse | Event/time-series | Views, carts, purchases |

## Native Query Problem

The business question requires SQL for users, Mongo queries for products, Cypher for recommendations, and event queries for recent behavior.

## SEMLINK Query

```sparql
PREFIX commerce: <https://semlink.example.org/commerce#>

SELECT ?user ?segment ?product ?reason ?eventCount WHERE {
  ?user a commerce:Customer ;
    commerce:segment "premium" ;
    commerce:viewed ?event ;
    commerce:recommendedProduct ?product .
  ?product commerce:category "Running Shoes" ;
    commerce:recommendationReason ?reason .
  ?event commerce:withinMinutes 60 .
  ?user commerce:recentEventCount ?eventCount .
}
ORDER BY DESC(?eventCount)
```

## Wow Moment

One query returns user profile, product catalog details, graph recommendation reason, and real-time event count with provenance.
