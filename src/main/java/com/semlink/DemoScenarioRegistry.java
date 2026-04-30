package com.semlink;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog of the five complete SEMLINK demo scenarios used in project
 * presentations.
 */
public class DemoScenarioRegistry {
    private final Map<String, DemoScenario> scenarios;

    private DemoScenarioRegistry(Map<String, DemoScenario> scenarios) {
        this.scenarios = new LinkedHashMap<>(scenarios);
    }

    public static DemoScenarioRegistry defaults() {
        Map<String, DemoScenario> scenarios = new LinkedHashMap<>();
        scenarios.put("usecase1", new DemoScenario(
            "usecase1",
            "AICTE Accreditation Dashboard",
            "AICTE audits four universities with relational, document, graph, and KV-style models.",
            "ER model to OWL mapping, heterogeneous schema alignment, and source-independent SPARQL.",
            List.of(
                "MySQL: SELECT COUNT(*) FROM tbl_students WHERE dept='CSE'",
                "MongoDB: db.students.countDocuments({department:'CS'})",
                "Neo4j: MATCH (s:Student)-[:BELONGS_TO]->(:Dept {name:'Computer Science'}) RETURN COUNT(s)",
                "Redis: SCAN student:*:profile and filter JSON department"
            ),
            """
                PREFIX aicte: <https://semlink.example.org/aicte#>
                SELECT ?universityName (COUNT(DISTINCT ?student) AS ?csStudents)
                WHERE {
                  ?student a aicte:Student ;
                    aicte:department ?department ;
                    aicte:studiesAt ?college .
                  ?college aicte:belongsToUniversity ?university .
                  ?university aicte:name ?universityName .
                  FILTER(LCASE(STR(?department)) = "computer science")
                }
                GROUP BY ?universityName
                ORDER BY DESC(?csStudents) ?universityName
                """,
            "One query. Four databases. Unified result with source provenance and owl:sameAs-aware identity resolution.",
            List.of("query cs_students_by_university", "report")
        ));
        scenarios.put("usecase2", new DemoScenario(
            "usecase2",
            "Cross-University Student Deduplication",
            "Transferred students appear under different identifiers in different university systems.",
            "Entity identity, owl:sameAs, and SimilarityMatcher-assisted canonicalization.",
            List.of(
                "SQL: SELECT * FROM tbl_students WHERE full_name LIKE 'A%Mehta%'",
                "Cypher: MATCH (s:Student {name:'A. Mehta'}) RETURN s",
                "SPARQL native source checks before canonical sameAs closure"
            ),
            """
                PREFIX aicte: <https://semlink.example.org/aicte#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT ?canonical ?name ?rollNo ?allSources WHERE {
                  ?canonical owl:sameAs* ?entity .
                  ?entity aicte:name ?name ; aicte:id ?rollNo .
                  BIND(STR(?entity) AS ?allSources)
                }
                """,
            "A deduplicated canonical student list appears even when source IDs differ.",
            List.of("query same_as_clusters", "query same_as_student_details")
        ));
        scenarios.put("usecase3", new DemoScenario(
            "usecase3",
            "Data Quality Report For Regulators",
            "The ministry needs clean-data evidence before funding release.",
            "SHACL constraint modelling, conformance, and quality scoring.",
            List.of(
                "Manual spreadsheet checks for missing names, invalid CGPA, and malformed course codes",
                "Separate SQL/Mongo/graph validation scripts per institution"
            ),
            """
                PREFIX sh: <http://www.w3.org/ns/shacl#>
                SELECT ?focusNode ?resultPath ?message WHERE {
                  ?result a sh:ValidationResult ;
                    sh:focusNode ?focusNode ;
                    sh:resultPath ?resultPath ;
                    sh:resultMessage ?message .
                }
                """,
            "A dark HTML report gives each university a visible compliance status and drill-down artifact path.",
            List.of("validate", "report")
        ));
        scenarios.put("usecase4", new DemoScenario(
            "usecase4",
            "Natural Language Query Interface",
            "A non-technical registrar asks a plain-English question.",
            "Ontology-grounded natural-language to SPARQL translation.",
            List.of(
                "Registrar asks: How many students in Computer Science scored above 8 CGPA in University3?",
                "Technical staff would otherwise write SPARQL manually"
            ),
            """
                PREFIX aicte: <https://semlink.example.org/aicte#>
                SELECT ?student ?name ?cgpa ?university WHERE {
                  ?student a aicte:Student ;
                    aicte:name ?name ;
                    aicte:cgpa ?cgpa ;
                    aicte:belongsToUniversity ?university .
                  FILTER(?cgpa > 9.0)
                }
                """,
            "The system prints generated SPARQL before execution, so AI assistance stays transparent.",
            List.of("nl Show students with CGPA above 9 from all universities")
        ));
        scenarios.put("usecase5", new DemoScenario(
            "usecase5",
            "New Institution Onboarding",
            "University5 has a MySQL database and no OWL expertise.",
            "Schema auto-discovery, R2RML mapping, Jena rules, and self-service onboarding.",
            List.of(
                "MySQL: DESCRIBE students; DESCRIBE departments; inspect foreign keys manually",
                "Manual mapping review in spreadsheets"
            ),
            """
                PREFIX aicte: <https://semlink.example.org/aicte#>
                SELECT ?student ?name WHERE {
                  ?student a aicte:Student ; aicte:name ?name .
                }
                LIMIT 10
                """,
            "Connect to queryable in 47 seconds: discover, map, validate, publish.",
            List.of("connect add --type mysql --host u5.example.edu --db students --user aicte", "pipeline run --source mysql")
        ));
        return new DemoScenarioRegistry(scenarios);
    }

    public List<DemoScenario> list() {
        return List.copyOf(scenarios.values());
    }

    public DemoScenario get(String id) {
        DemoScenario scenario = scenarios.get(id);
        if (scenario == null) {
            throw new IllegalArgumentException("Unknown use case: " + id + ". Available: " + scenarios.keySet());
        }
        return scenario;
    }
}
