package com.semlink.onboarding;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.semlink.SqlInputParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LLMMappingService {
    private static final Set<String> NAME_HINTS = Set.of("name", "title", "label", "nm", "full_nm");
    private static final Map<String, List<String>> CLASS_HINTS = Map.of(
        "University", List.of("university", "hub", "parentuniversity"),
        "College", List.of("college", "institute", "campus", "affiliatedcollege"),
        "Student", List.of("student", "learner", "pupil", "studentinfo"),
        "Course", List.of("course", "module", "subject", "paper"),
        "Department", List.of("department", "school", "division", "faculty"),
        "Program", List.of("program", "track", "plan")
    );

    private final Client geminiClient;
    private final boolean llmEnabled;

    public LLMMappingService(String geminiApiKey) {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            this.geminiClient = Client.builder().apiKey(geminiApiKey).build();
            this.llmEnabled = true;
        } else {
            this.geminiClient = null;
            this.llmEnabled = false;
        }
    }

    public List<MappingSuggestion> generateSuggestions(SqlInputParser.SchemaStatistics stats) {
        if (llmEnabled) {
            try {
                return generateWithLLM(stats);
            } catch (Exception e) {
                System.err.println("LLM mapping failed, falling back to heuristics: " + e.getMessage());
            }
        }
        return generateWithHeuristics(stats);
    }

    public String generateR2rml(SqlInputParser.SchemaStatistics stats, String universityName) {
        if (!llmEnabled) {
            throw new IllegalStateException("Gemini API key not found. Cannot automate mapping generation.");
        }
        String prompt = buildR2rmlPrompt(stats, universityName);
        GenerateContentResponse response = geminiClient.models.generateContent(
            "gemini-3-flash-preview", prompt, null);
        String text = response.text();
        if (text == null) return null;
        return text.replace("```turtle", "").replace("```", "").trim();
    }

    public boolean isLLMEnabled() {
        return llmEnabled;
    }

    private List<MappingSuggestion> generateWithLLM(SqlInputParser.SchemaStatistics stats) {
        String prompt = buildPrompt(stats);
        GenerateContentResponse response = geminiClient.models.generateContent(
            "gemini-3-flash-preview", prompt, null);
        String text = response.text();
        if (text == null || text.isBlank()) {
            return generateWithHeuristics(stats);
        }
        text = text.replace("```json", "").replace("```", "").trim();
        return parseLLMResponse(text, stats);
    }

    private String buildPrompt(SqlInputParser.SchemaStatistics stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert RDB-to-ontology mapper. Map the following schema to the AICTE ontology.\n\n");
        sb.append("ONTOLOGY:\n");
        sb.append("  Classes: Student, College, University, Course, Department, Program\n");
        sb.append("  DatatypeProperties: id, name, cgpa, department\n");
        sb.append("  ObjectProperties: studiesAt (Student->College), belongsToUniversity (College->University),\n");
        sb.append("    offersCourse (College->Course), memberOfDepartment (Student->Department), enrolledIn (Student->Course)\n\n");
        sb.append("SCHEMA:\n");
        for (SqlInputParser.TableStats table : stats.tables()) {
            sb.append("- Table: ").append(table.name()).append("\n");
            for (SqlInputParser.ColumnStats col : table.columns()) {
                sb.append("    Column: ").append(col.name())
                  .append(" | Type: ").append(col.sqlType())
                  .append(" | Nullable: ").append(col.nullable())
                  .append(" | Samples: ").append(col.sampleValues()).append("\n");
            }
            if (!table.foreignKeys().isEmpty()) {
                for (SqlInputParser.ForeignKeyRef fk : table.foreignKeys()) {
                    sb.append("    FK: ").append(fk.column()).append(" -> ").append(fk.references()).append("\n");
                }
            }
            sb.append("    RowCount: ").append(table.rowCount()).append("\n");
        }
        sb.append("\nTASK:\n");
        sb.append("Output a JSON array (no markdown) where each element has:\n");
        sb.append("\"sourceTable\": table name,\n");
        sb.append("\"sourceColumn\": column name,\n");
        sb.append("\"suggestedClass\": AICTE class,\n");
        sb.append("\"suggestedProperty\": AICTE property,\n");
        sb.append("\"confidence\": 0.0-1.0,\n");
        sb.append("\"rationale\": brief reason,\n");
        sb.append("\"matchType\": \"property\" or \"relationship\"\n");
        sb.append("Only output the JSON array. No explanation.\n");
        return sb.toString();
    }

    private String buildR2rmlPrompt(SqlInputParser.SchemaStatistics stats, String universityName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert R2RML author. Generate a valid R2RML Turtle mapping for the following SQL schema.\n\n");
        sb.append("TARGET ONTOLOGY:\n");
        sb.append("  Namespace: https://semlink.example.org/aicte# (prefix aicte:)\n");
        sb.append("  Classes: Student, College, University, Course, Department\n");
        sb.append("  Properties: id, name, cgpa, department, studiesAt, belongsToUniversity\n\n");
        sb.append("RULES:\n");
        sb.append("  - Use templates for subject URIs: https://semlink.example.org/universities/").append(universityName).append("/{table}/{id}\n");
        sb.append("  - Map local IDs to aicte:id.\n");
        sb.append("  - Map labels/names to aicte:name.\n");
        sb.append("  - Map foreign keys to aicte:studiesAt or aicte:belongsToUniversity where applicable.\n\n");
        sb.append("SCHEMA:\n");
        for (SqlInputParser.TableStats table : stats.tables()) {
            sb.append("- Table: ").append(table.name()).append("\n");
            for (SqlInputParser.ColumnStats col : table.columns()) {
                sb.append("    Column: ").append(col.name()).append(" (").append(col.sqlType()).append(")\n");
            }
        }
        sb.append("\nOutput ONLY the R2RML Turtle code. No markdown code blocks, no explanation.\n");
        return sb.toString();
    }

    private List<MappingSuggestion> parseLLMResponse(String json, SqlInputParser.SchemaStatistics stats) {
        List<MappingSuggestion> suggestions = new ArrayList<>();
        try {
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (var elem : arr) {
                var obj = elem.getAsJsonObject();
                suggestions.add(new MappingSuggestion(
                    obj.get("sourceTable").getAsString(),
                    obj.get("sourceColumn").getAsString(),
                    obj.get("suggestedClass").getAsString(),
                    obj.get("suggestedProperty").getAsString(),
                    obj.get("confidence").getAsDouble(),
                    obj.has("rationale") ? obj.get("rationale").getAsString() : "",
                    obj.has("matchType") ? obj.get("matchType").getAsString() : MappingSuggestion.TYPE_PROPERTY
                ));
            }
        } catch (Exception e) {
            System.err.println("Failed to parse LLM response, falling back: " + e.getMessage());
            return generateWithHeuristics(stats);
        }
        return suggestions;
    }

    public List<MappingSuggestion> generateWithHeuristics(SqlInputParser.SchemaStatistics stats) {
        List<MappingSuggestion> suggestions = new ArrayList<>();
        for (SqlInputParser.TableStats table : stats.tables()) {
            String guessedClass = guessClass(table.name());
            for (SqlInputParser.ColumnStats col : table.columns()) {
                double confidence;
                String property;
                String rationale;

                String lowerCol = col.name().toLowerCase();
                if (col.name().equalsIgnoreCase("id") || lowerCol.endsWith("_id")) {
                    if (lowerCol.contains("dept") || lowerCol.contains("brn")) {
                        property = "department";
                        confidence = 0.68;
                        rationale = "ID column with department hint matches 'department' property";
                    } else {
                        property = "id";
                        confidence = 0.99;
                        rationale = "ID column maps to 'id' property";
                    }
                } else if (isNameColumn(col.name())) {
                    property = "name";
                    confidence = 0.94;
                    rationale = "Name-like column maps to 'name' property";
                } else if (lowerCol.contains("cgpa") || lowerCol.contains("gpa")) {
                    property = "cgpa";
                    confidence = 0.97;
                    rationale = "CGPA column detected";
                } else if (lowerCol.contains("dept") || lowerCol.contains("department")) {
                    property = "department";
                    confidence = 0.71;
                    rationale = "Department column maps to 'department' property";
                } else {
                    property = "unknown";
                    confidence = 0.30;
                    rationale = "No clear mapping, user review required";
                }

                suggestions.add(new MappingSuggestion(
                    table.name(), col.name(), guessedClass, property, confidence, rationale, MappingSuggestion.TYPE_PROPERTY
                ));
            }

            for (SqlInputParser.ForeignKeyRef fk : table.foreignKeys()) {
                String targetTable = fk.references().split("\\.")[0];
                String relationship = guessRelationship(table.name(), fk.column(), targetTable);
                if (relationship != null) {
                    suggestions.add(new MappingSuggestion(
                        table.name(), fk.column(),
                        guessClass(targetTable), relationship, 0.90,
                        "Foreign key implies " + relationship + " relationship", MappingSuggestion.TYPE_RELATIONSHIP
                    ));
                }
            }
        }
        return suggestions;
    }

    private String guessClass(String tableName) {
        String normalized = tableName.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CLASS_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (normalized.contains(hint)) return entry.getKey();
            }
        }
        return "Entity";
    }

    private boolean isNameColumn(String colName) {
        String lower = colName.toLowerCase();
        for (String hint : NAME_HINTS) {
            if (lower.contains(hint)) return true;
        }
        return false;
    }

    private String guessRelationship(String fromTable, String fkColumn, String toTable) {
        String fromClass = guessClass(fromTable);
        String toClass = guessClass(toTable);
        if ("Student".equals(fromClass) && "College".equals(toClass)) return "studiesAt";
        if ("College".equals(fromClass) && "University".equals(toClass)) return "belongsToUniversity";
        if ("Student".equals(fromClass) && "Department".equals(toClass)) return "memberOfDepartment";
        if ("Course".equals(fromClass) && "College".equals(toClass)) return "offersCourse";
        return null;
    }
}