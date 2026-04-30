package com.semlink;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.apache.jena.query.QueryFactory;

/**
 * Ontology-grounded natural-language to SPARQL translator. The local fallback
 * keeps demos deterministic; a Gemini API integration can be enabled for richer
 * generation while still validating output with Jena before execution.
 */
public class NLQueryTranslator {
    private final Client client;
    private final boolean isRemoteEnabled;

    private NLQueryTranslator(String geminiApiKey) {
        Client initClient = null;
        if (geminiApiKey != null && !geminiApiKey.isEmpty()) {
            initClient = Client.builder().apiKey(geminiApiKey).build();
        } else {
            try {
                initClient = new Client();
            } catch (Exception e) {
                // Remote generation disabled
            }
        }
        this.client = initClient;
        this.isRemoteEnabled = (this.client != null);
        System.out.println("Remote LLM enabled: " + isRemoteEnabled);
    }

    public static NLQueryTranslator withoutRemoteModel() {
        return new NLQueryTranslator(null);
    }

    public static NLQueryTranslator withGemini(String apiKey) {
        return new NLQueryTranslator(apiKey);
    }

    public String translate(String question) {
        if (isRemoteEnabled) {
            String generated = tryRemote(question);
            if (isValidSparql(generated)) {
                return generated;
            }
        }
        return deterministicFallback(question);
    }

    private String deterministicFallback(String question) {
        String lower = question == null ? "" : question.toLowerCase();
        if (lower.contains("cgpa") || lower.contains("above 9")) {
            return """
                    PREFIX aicte: <https://semlink.example.org/aicte#>
                    SELECT ?student ?name ?cgpa ?university WHERE {
                      ?student a aicte:Student ;
                               aicte:name ?name ;
                               aicte:cgpa ?cgpa ;
                               aicte:belongsToUniversity ?university .
                      FILTER(?cgpa > 9.0)
                    }
                    ORDER BY DESC(?cgpa)
                    """;
        }
        if (lower.contains("more than 5 courses")) {
            return """
                    PREFIX aicte: <https://semlink.example.org/aicte#>
                    SELECT ?student ?name (COUNT(DISTINCT ?course) AS ?courseCount) WHERE {
                      ?student a aicte:Student ;
                               aicte:name ?name ;
                               aicte:enrolledIn ?course .
                    }
                    GROUP BY ?student ?name
                    HAVING(COUNT(DISTINCT ?course) > 5)
                    """;
        }
        return """
                PREFIX aicte: <https://semlink.example.org/aicte#>
                SELECT ?student ?name WHERE {
                  ?student a aicte:Student ;
                           aicte:name ?name .
                }
                LIMIT 25
                """;
    }

    private String tryRemote(String question) {
        String prompt = """
                You are an expert SPARQL generator. Convert the natural language question into a valid SPARQL query for the AICTE ontology.
                The ontology uses the prefix aicte: <https://semlink.example.org/aicte#>

                Classes available:
                - aicte:Student, aicte:College, aicte:University, aicte:Course, aicte:Department, aicte:Program

                Properties available:
                - aicte:studiesAt (Domain: Student, Range: College)
                - aicte:belongsToUniversity (Domain: College, Range: University)
                - aicte:offersCourse (Domain: College, Range: Course)
                - aicte:memberOfDepartment (Domain: Student, Range: Department)
                - aicte:enrolledIn (Domain: Student, Range: Course)
                - aicte:name (Datatype property for names)
                - aicte:id (Datatype property for IDs)
                - aicte:cgpa (Datatype property for CGPA)

                Important instructions:
                1. ONLY output the raw SPARQL query.
                2. DO NOT include markdown formatting like ```sparql or ```.
                3. DO NOT include any explanations.

                Question: """
                + question;

        try {
            GenerateContentResponse response = client.models.generateContent("gemini-3-flash-preview", prompt, null);
            String extracted = response.text();
            if (extracted != null) {
                // Remove potential markdown formatting that might still be present
                extracted = extracted.replace("```sparql\n", "").replace("```\n", "").replace("```", "").trim();
                return extracted;
            }
            return "";
        } catch (Exception exception) {
            System.err.println("Gemini API error: " + exception.getMessage());
            exception.printStackTrace();
            return "";
        }
    }

    private boolean isValidSparql(String sparql) {
        try {
            QueryFactory.create(sparql);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
