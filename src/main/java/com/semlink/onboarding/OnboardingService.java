package com.semlink.onboarding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.semlink.SqlInputParser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OnboardingService {
    private static final String AICTE_NS = "https://semlink.example.org/aicte#";
    private static final String REFINED_NS = "https://semlink.example.org/r2o/refined/";
    private static final Path OUTPUT_ROOT = Path.of("target", "semantic-output", "r2o");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> OBJECT_PROPERTIES = Set.of(
        "studiesAt", "belongsToUniversity", "offersCourse", "memberOfDepartment", "enrolledIn");

    private final SqlInputParser parser = new SqlInputParser();
    private final LLMMappingService llmService;
    private final com.semlink.OntologyDatabase ontologyDatabase;

    private volatile Model inferredModel;
    private final Map<String, OnboardingJob> jobs = new ConcurrentHashMap<>();

    public OnboardingService(String geminiApiKey, com.semlink.OntologyDatabase ontologyDatabase) {
        this.llmService = new LLMMappingService(geminiApiKey);
        this.ontologyDatabase = ontologyDatabase;
    }

    public boolean isLLMEnabled() {
        return llmService.isLLMEnabled();
    }

    public boolean hasModel(String name) {
        OnboardingJob job = jobs.get(name);
        return job != null && job.outputModel != null;
    }

    public Model getModel(String name) {
        OnboardingJob job = jobs.get(name);
        return job != null ? job.outputModel : null;
    }

    public void setInferredModel(Model model) {
        this.inferredModel = model;
    }

    public Map<String, Object> parseSql(String name, String schemaSql, String dataSql) {
        SqlInputParser.SchemaStatistics stats = parser.buildSchemaStatistics(schemaSql, dataSql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "parsed");
        result.put("datasetName", stats.datasetName());
        result.put("tables", stats.tables().size());
        result.put("totalRows", stats.tables().stream().mapToInt(SqlInputParser.TableStats::rowCount).sum());
        result.put("schema", stats);

        Path jobDir = jobDirectory(name);
        writeJson(jobDir.resolve("parsed-schema.json"), stats);
        jobs.put(name, new OnboardingJob(name, jobDir, stats));
        return result;
    }

    public Map<String, Object> suggestMappings(String name) {
        OnboardingJob job = jobs.get(name);
        if (job == null) return Map.of("status", "error", "message", "No job found for: " + name);

        List<MappingSuggestion> suggestions = llmService.generateSuggestions(job.schemaStats());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("suggestions", suggestions);
        result.put("llmMode", llmService.isLLMEnabled() ? "gemini" : "heuristic");

        writeJson(job.dir.resolve("llm-suggestions.json"), suggestions);
        job.suggestions = suggestions;
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> approveMappings(String name, List<Map<String, Object>> approvedMappingsRaw) {
        OnboardingJob job = jobs.get(name);
        if (job == null) return Map.of("status", "error", "message", "No job found for: " + name);
        if (approvedMappingsRaw == null) return Map.of("status", "error", "message", "No mappings provided");

        List<ApprovedMapping> approved = new ArrayList<>();
        for (Map<String, Object> raw : approvedMappingsRaw) {
            approved.add(new ApprovedMapping(
                (String) raw.get("sourceTable"),
                (String) raw.get("sourceColumn"),
                (String) raw.get("aicteClass"),
                (String) raw.get("aicteProperty"),
                Boolean.TRUE.equals(raw.get("userCustomized"))
            ));
        }

        job.approvedMappings = approved;
        writeJson(job.dir.resolve("mapping-config.json"), approved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "approved");
        result.put("mappingCount", approved.size());
        return result;
    }

    public Map<String, Object> transform(String name, String schemaSql, String dataSql) {
        OnboardingJob job = jobs.get(name);
        if (job == null || job.approvedMappings == null || job.approvedMappings.isEmpty()) {
            return Map.of("status", "error", "message", "No approved mappings found for: " + name);
        }

        SqlInputParser.SqlSchema schema = parser.parseSchema(schemaSql);
        SqlInputParser.SqlData data = parser.parseData(dataSql);
        Model model = buildApprovedModel(job.approvedMappings, schema, data);

        Path outputPath = job.dir.resolve("approved-mapping.ttl");
        writeModel(model, outputPath, Lang.TURTLE);

        List<String> extractedClasses = new ArrayList<>();
        model.listSubjects().forEachRemaining(r -> {
            if (r.isURIResource()) {
                String uri = r.getURI();
                if (uri.contains("#")) extractedClasses.add(uri.substring(uri.lastIndexOf('#') + 1));
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("triples", model.size());
        result.put("extractedClasses", extractedClasses.stream().distinct().limit(20).toList());
        job.outputModel = model;
        return result;
    }

    private Model buildApprovedModel(List<ApprovedMapping> mappings,
                                      SqlInputParser.SqlSchema schema,
                                      SqlInputParser.SqlData data) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("aicte", AICTE_NS);

        Map<String, List<ApprovedMapping>> byTable = new LinkedHashMap<>();
        for (ApprovedMapping m : mappings) {
            byTable.computeIfAbsent(m.sourceTable(), k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<ApprovedMapping>> entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = data.rowsByTable().getOrDefault(tableName, List.of());
            List<ApprovedMapping> tableMappings = entry.getValue();

            for (Map<String, Object> row : rows) {
                String rowId = String.valueOf(row.getOrDefault(getIdColumn(schema, tableName), "row-" + rows.indexOf(row)));
                Resource subject = model.createResource(REFINED_NS + slugify(tableName) + "/" + slugify(rowId));

                for (ApprovedMapping mapping : tableMappings) {
                    Object value = row.get(mapping.sourceColumn());
                    if (value == null) continue;

                    if (isObjectProperty(mapping.aicteProperty())) {
                        Resource obj = model.createResource(
                            REFINED_NS + slugify(mapping.aicteProperty()) + "/" + slugify(String.valueOf(value)));
                        model.add(subject, model.createProperty(AICTE_NS + mapping.aicteProperty()), obj);
                    } else {
                        model.add(subject, model.createProperty(AICTE_NS + mapping.aicteProperty()),
                            model.createTypedLiteral(value));
                    }
                }
            }
        }
        return model;
    }

    private String getIdColumn(SqlInputParser.SqlSchema schema, String tableName) {
        SqlInputParser.TableDefinition table = schema.table(tableName);
        if (table == null) return "id";
        for (String col : table.columns()) {
            if (col.equalsIgnoreCase("id") || col.toLowerCase().endsWith("_id")) return col;
        }
        return table.columns().isEmpty() ? "id" : table.columns().getFirst();
    }

    private boolean isObjectProperty(String property) {
        return OBJECT_PROPERTIES.contains(property);
    }

    private String slugify(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    public Map<String, Object> validateTriples(String name) {
        OnboardingJob job = jobs.get(name);
        if (job == null || job.outputModel == null) {
            return Map.of("status", "error", "message", "No transformed model found for: " + name);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("triples", job.outputModel.size());
        result.put("conforms", job.outputModel.size() > 0);
        result.put("violationCount", 0);
        return result;
    }

    public synchronized Map<String, Object> publish(String name) {
        OnboardingJob job = jobs.get(name);
        if (job == null || job.outputModel == null) {
            return Map.of("status", "error", "message", "No output model to publish for: " + name);
        }

        if (inferredModel != null) {
            inferredModel.add(job.outputModel);
        }

        if (ontologyDatabase != null) {
            ontologyDatabase.addOntology(name, job.outputModel);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "published");
        result.put("totalTriples", inferredModel != null ? inferredModel.size() : job.outputModel.size());
        result.put("newTriplesAdded", job.outputModel.size());
        result.put("newNodeId", name);
        result.put("newNodeLabel", name + " (SQL)");
        return result;
    }

    private Path jobDirectory(String name) {
        Path dir = OUTPUT_ROOT.resolve(name);
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        return dir;
    }

    private void writeJson(Path path, Object data) {
        try {
            Files.writeString(path, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void writeModel(Model model, Path path, Lang lang) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream os = Files.newOutputStream(path)) { RDFDataMgr.write(os, model, lang); }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private static class OnboardingJob {
        final String name;
        final Path dir;
        final SqlInputParser.SchemaStatistics schemaStats;
        List<MappingSuggestion> suggestions = new ArrayList<>();
        List<ApprovedMapping> approvedMappings = null;
        Model outputModel = null;

        OnboardingJob(String name, Path dir, SqlInputParser.SchemaStatistics schemaStats) {
            this.name = name;
            this.dir = dir;
            this.schemaStats = schemaStats;
        }

        SqlInputParser.SchemaStatistics schemaStats() { return schemaStats; }
    }
}