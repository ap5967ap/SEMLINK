package com.semlink;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.apache.jena.query.QueryFactory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ontology-grounded natural-language to SPARQL translator. The local fallback
 * keeps demos deterministic; a Gemini API integration can be enabled for richer
 * generation while still validating output with Jena before execution.
 */
public class NLQueryTranslator {
    private static final Pattern SOURCE_LABEL_PATTERN = Pattern.compile("\\buniversity([1-9]\\d*)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_LABEL_LITERAL_PATTERN = Pattern.compile("\"university\\d+\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern THRESHOLD_PATTERN = Pattern.compile(
        "(?:cgpa|gpa|above|over|greater than|more than|>)\\s*(?:is\\s*)?(\\d+(?:\\.\\d+)?)",
        Pattern.CASE_INSENSITIVE);

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
        if (mentionsSourceLabel(question)) {
            return deterministicFallback(question);
        }
        if (isRemoteEnabled) {
            String generated = tryRemote(question);
            if (isUsableRemoteQuery(generated, question)) {
                return generated;
            }
        }
        return deterministicFallback(question);
    }

    private String deterministicFallback(String question) {
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String sourceLabel = extractSourceLabel(question);
        Double minCgpa = extractMinCgpa(question);

        if (lower.contains("student") || lower.contains("cgpa") || lower.contains("gpa")) {
            return buildStudentQuery(sourceLabel, minCgpa);
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
                - aicte:department (Domain: Student, Range: Department or String)
                - aicte:enrolledIn (Domain: Student, Range: Course)
                - aicte:name (Datatype property for names)
                - aicte:id (Datatype property for IDs)
                - aicte:cgpa (Datatype property for CGPA)

                Known source-label mappings:
                - "university1" is a source label, not an aicte:id literal. Its students study at u1:EnggCollege_01.
                - "university2" maps to u2:BeaconUniversity.
                - "university3" maps to u3:NorthStateUniversity.
                - "university4" maps to u4:KnowledgeGridUniversity.
                - "university7" maps to <https://semlink.example.org/universities/university7/u7_main/7>.

                Important instructions:
                1. ONLY output the raw SPARQL query.
                2. DO NOT include markdown formatting like ```sparql or ```.
                3. DO NOT include any explanations.
                4. Use LCASE(STR(?var)) for department name filters to handle variations like "CSE" vs "Computer Science".
                5. NEVER generate filters like aicte:id "university1" or FILTER(... = "university7"). Those source labels are not data values.
                6. Do not attach aicte:belongsToUniversity directly to aicte:Student. It belongs on the college node.
                7. If the user asks for a source label such as university1, use the mapped resource above instead of inventing an aicte:id filter.

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

    private boolean isUsableRemoteQuery(String sparql, String question) {
        if (!isValidSparql(sparql)) {
            return false;
        }
        if (hasUnsupportedSourceLabelFilter(sparql)) {
            return false;
        }
        if (mentionsSourceLabel(question) && sparql.contains("aicte:belongsToUniversity ?university .")
                && sparql.contains("?university aicte:id")) {
            return false;
        }
        if (sparql.contains("?student aicte:belongsToUniversity")) {
            return false;
        }
        return true;
    }

    private boolean hasUnsupportedSourceLabelFilter(String sparql) {
        return SOURCE_LABEL_LITERAL_PATTERN.matcher(sparql).find();
    }

    private boolean isValidSparql(String sparql) {
        try {
            QueryFactory.create(sparql);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String extractSourceLabel(String question) {
        if (question == null) {
            return null;
        }
        Matcher matcher = SOURCE_LABEL_PATTERN.matcher(question);
        return matcher.find() ? ("university" + matcher.group(1)) : null;
    }

    private boolean mentionsSourceLabel(String question) {
        return extractSourceLabel(question) != null;
    }

    private Double extractMinCgpa(String question) {
        if (question == null) {
            return null;
        }
        Matcher matcher = THRESHOLD_PATTERN.matcher(question);
        Double value = null;
        while (matcher.find()) {
            value = Double.parseDouble(matcher.group(1));
        }
        return value;
    }

    private String buildStudentQuery(String sourceLabel, Double minCgpa) {
        String normalizedSource = sourceLabel == null ? "" : sourceLabel.toLowerCase(Locale.ROOT);
        String nameProperty = "aicte:name";
        String cgpaProperty = "aicte:cgpa";
        if ("university1".equals(normalizedSource)) {
            nameProperty = "u1:full_name";
            cgpaProperty = "u1:cgpa";
        }

        StringBuilder prefixes = new StringBuilder("PREFIX aicte: <https://semlink.example.org/aicte#>\n");
        StringBuilder body = new StringBuilder();
        body.append("SELECT ?student ?name");
        if (minCgpa != null) {
            body.append(" ?cgpa");
        }
        body.append("\nWHERE {\n");
        body.append("  ?student a aicte:Student ;\n");
        body.append("           ").append(nameProperty).append(" ?name");
        if (minCgpa != null) {
            body.append(" ;\n");
            body.append("           ").append(cgpaProperty).append(" ?cgpa");
        }
        body.append(" .\n");
        appendSourceFilter(prefixes, body, normalizedSource);
        if (minCgpa != null) {
            body.append("  FILTER(?cgpa > ").append(formatNumber(minCgpa)).append(")\n");
        }
        body.append("}\n");
        if (minCgpa != null) {
            body.append("ORDER BY DESC(?cgpa)\n");
        } else {
            body.append("ORDER BY ?name\n");
        }
        return prefixes.append(body).toString();
    }

    private void appendSourceFilter(StringBuilder prefixes, StringBuilder body, String sourceLabel) {
        if (sourceLabel == null) {
            return;
        }
        switch (sourceLabel) {
            case "university1" -> {
                prefixes.append("PREFIX u1: <http://example.org/collegeone#>\n");
                body.append("  ?student aicte:studiesAt u1:EnggCollege_01 .\n");
            }
            case "university2" -> {
                prefixes.append("PREFIX u2: <https://semlink.example.org/university2#>\n");
                body.append("  ?student aicte:studiesAt ?college .\n");
                body.append("  ?college aicte:belongsToUniversity u2:BeaconUniversity .\n");
            }
            case "university3" -> {
                prefixes.append("PREFIX u3: <https://semlink.example.org/university3#>\n");
                body.append("  ?student aicte:studiesAt ?college .\n");
                body.append("  ?college aicte:belongsToUniversity u3:NorthStateUniversity .\n");
            }
            case "university4" -> {
                prefixes.append("PREFIX u4: <https://semlink.example.org/university4#>\n");
                body.append("  ?student aicte:studiesAt ?college .\n");
                body.append("  ?college aicte:belongsToUniversity u4:KnowledgeGridUniversity .\n");
            }
            case "university7" -> {
                body.append("  ?student aicte:studiesAt ?college .\n");
                body.append("  ?college aicte:belongsToUniversity <https://semlink.example.org/universities/university7/u7_main/7> .\n");
            }
            default -> {
            }
        }
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return Double.toString(value);
    }
}
