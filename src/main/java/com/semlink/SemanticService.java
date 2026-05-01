package com.semlink;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core service that wraps all semantic pipeline logic for the REST API.
 * Thread-safe: the inferred model is built once and cached.
 */
@Service
public class SemanticService {
    private static final Logger log = LoggerFactory.getLogger(SemanticService.class);

    private static final List<String[]> ONTOLOGY_RESOURCES = List.of(
        new String[]{"university1", "semantic/ontologies/local/university1/university1.ttl"},
        new String[]{"university2", "semantic/ontologies/local/university2/university2.ttl"},
        new String[]{"university3", "semantic/ontologies/local/university3/university3.ttl"},
        new String[]{"university4", "semantic/ontologies/local/university4/university4.ttl"},
        new String[]{"aicte",       "semantic/ontologies/central/aicte.ttl"}
    );

    private static final Path OUTPUT_DIR = Path.of("target", "semantic-output");
    private static final String SHAPES_RESOURCE = "semantic/shapes/aicte-shapes.ttl";
    private static final String RULE_RESOURCE   = "semantic/rules/alignment.rules";

    private final QueryEngine queryEngine = new QueryEngine();
    private final SimilarityMatcher similarityMatcher = new SimilarityMatcher("https://semlink.example.org/aicte#");
    private final NLQueryTranslator nlTranslator;
    private final OntologyDatabase ontologyDatabase;
    private final String apiKey;

    public SemanticService(OntologyDatabase ontologyDatabase) {
        this.ontologyDatabase = ontologyDatabase;
        
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = loadEnvFile("GEMINI_API_KEY");
        }
        this.apiKey = apiKey;
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("Gemini API key found, enabling AI query translation");
            nlTranslator = NLQueryTranslator.withGemini(apiKey);
        } else {
            log.info("No Gemini API key, using deterministic fallback");
            nlTranslator = NLQueryTranslator.withoutRemoteModel();
        }

        // Initialize TDB2 database with default ontologies if empty
        if (this.ontologyDatabase.isEmpty()) {
            log.info("Database is empty, loading default ontologies...");
            for (String[] res : ONTOLOGY_RESOURCES) {
                Model m = loadModel(res[1]);
                this.ontologyDatabase.addOntology(res[0], m);
            }
        }
    }


    private static String loadEnvFile(String key) {
        try {
            Path envFile = Path.of(".env");
            if (!Files.exists(envFile)) return null;
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.startsWith("#") || !line.contains("=")) continue;
                String[] parts = line.split("=", 2);
                if (parts[0].trim().equals(key)) return parts[1].trim();
            }
        } catch (IOException e) { /* ignore */ }
        return null;
    }

    // Cached pipeline state
    private volatile Model inferredModel;
    private volatile boolean pipelineRan = false;
    private final Map<String, ConnectionConfig> connections = new ConcurrentHashMap<>();
    private com.semlink.onboarding.OnboardingService onboardingService;

    // Called by ApiController to wire the onboarding service
    public void setOnboardingService(com.semlink.onboarding.OnboardingService svc) {
        this.onboardingService = svc;
    }

    /* ── Pipeline ─────────────────────────────────────────────── */

    public synchronized Map<String, Object> runPipeline() {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.createDirectories(OUTPUT_DIR.resolve("query-results"));
            Files.createDirectories(OUTPUT_DIR.resolve("validation"));

            Model base = ontologyDatabase.getAllOntologiesMerged();
            inferredModel = createInferredModel(base);
            pipelineRan = true;
            if (onboardingService != null) onboardingService.setInferredModel(inferredModel);

            writeModel(base, OUTPUT_DIR.resolve("merged.ttl"), Lang.TURTLE);
            writeModel(inferredModel, OUTPUT_DIR.resolve("inferred.ttl"), Lang.TURTLE);

            Map<String, String> qr = queryEngine.runAll(inferredModel);
            for (var e : qr.entrySet()) {
                writeString(OUTPUT_DIR.resolve("query-results").resolve(e.getKey() + ".txt"), e.getValue());
            }

            ValidationReport report = validate(inferredModel);
            writeModel(report.getModel(), OUTPUT_DIR.resolve("validation/valid-report.ttl"), Lang.TURTLE);

            // Fetch aicte ontology explicitly for mapping suggestions
            Model aicteModel = ontologyDatabase.getOntology("aicte");
            Map<String, Model> ontologyModels = new LinkedHashMap<>();
            ontologyDatabase.listOntologies().forEach(n -> ontologyModels.put(n, ontologyDatabase.getOntology(n)));
            
            String suggestions = similarityMatcher.generateSuggestions(aicteModel, ontologyModels);
            writeString(OUTPUT_DIR.resolve("mapping-suggestions.tsv"), suggestions);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "completed");
            result.put("studentsInferred", countResources("https://semlink.example.org/aicte#Student"));
            result.put("collegesInferred", countResources("https://semlink.example.org/aicte#College"));
            result.put("coursesInferred",  countResources("https://semlink.example.org/aicte#Course"));
            result.put("universitiesInferred", countResources("https://semlink.example.org/aicte#University"));
            result.put("validationConforms", report.conforms());
            result.put("queriesExecuted", qr.size());
            log.info("Pipeline completed");
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Pipeline failed", e);
        }
    }

    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("pipelineRan", pipelineRan);
        if (pipelineRan && inferredModel != null) {
            health.put("studentsInferred", countResources("https://semlink.example.org/aicte#Student"));
            health.put("universitiesInferred", countResources("https://semlink.example.org/aicte#University"));
            health.put("totalTriples", inferredModel.size());
        }
        health.put("connectionsCount", listConnections().size());
        return health;
    }

    public Map<String, Object> refreshHealthAfterOnboarding(String onboardedSourceName) {
        // Recompute health metrics after onboarding publish
        return getHealth();
    }

    private void ensurePipeline() {
        if (!pipelineRan || inferredModel == null) {
            runPipeline();
        }
    }

    /* ── Queries ──────────────────────────────────────────────── */

    public List<String> listQueries() {
        return queryEngine.listQueries();
    }

    public Map<String, Object> executeSparql(String sparql) {
        ensurePipeline();
        Query query = QueryFactory.create(sparql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sparql", sparql);
        try (QueryExecution exec = QueryExecutionFactory.create(query, inferredModel)) {
            if (query.isSelectType()) {
                ResultSet rs = exec.execSelect();
                List<String> vars = rs.getResultVars();
                List<Map<String, String>> rows = new ArrayList<>();
                while (rs.hasNext()) {
                    QuerySolution sol = rs.next();
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String v : vars) {
                        var node = sol.get(v);
                        row.put(v, node == null ? "" : node.isLiteral() ? sol.getLiteral(v).getString() : node.toString());
                    }
                    rows.add(row);
                }
                result.put("columns", vars);
                result.put("rows", rows);
            } else if (query.isAskType()) {
                result.put("answer", exec.execAsk());
            }
        }
        return result;
    }

    public Map<String, Object> executeNamedQuery(String name) {
        ensurePipeline();
        String output = queryEngine.run(name, inferredModel);
        return Map.of("name", name, "raw", output);
    }

    public Map<String, Object> naturalLanguageQuery(String question) {
        String sparql = nlTranslator.translate(question);
        Map<String, Object> result = executeSparql(sparql);
        result.put("originalQuestion", question);
        result.put("generatedSparql", sparql);
        return result;
    }

    /* ── Validation ──────────────────────────────────────────── */

    public Map<String, Object> runValidation() {
        ensurePipeline();
        ValidationReport report = validate(inferredModel);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conforms", report.conforms());
        result.put("violationCount", report.getEntries().size());
        List<Map<String, String>> entries = new ArrayList<>();
        report.getEntries().forEach(e -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("focusNode", e.focusNode() == null ? "" : e.focusNode().toString());
            entry.put("path", e.resultPath() == null ? "" : e.resultPath().toString());
            entry.put("message", e.message() == null ? "" : e.message());
            entry.put("severity", e.severity() == null ? "" : e.severity().toString());
            entries.add(entry);
        });
        result.put("entries", entries);
        return result;
    }

    /* ── Connections ──────────────────────────────────────────── */

    public ConnectionConfig addConnection(String id, String type, String host, int port, String database) {
        ConnectionConfig config = new ConnectionConfig(id, type, host, port, database, "", "", false, 30, Map.of());
        connections.put(id, config);
        return config;
    }

    public Collection<ConnectionConfig> listConnections() {
        List<ConnectionConfig> all = new ArrayList<>(connections.values());
        for (String ontName : ontologyDatabase.listOntologies()) {
            all.add(new ConnectionConfig(ontName, "owl", "target/tdb-database", 0, "TDB2 Database", "", "", false, 0, Map.of()));
        }
        return all;
    }

    public boolean removeConnection(String id) {
        return connections.remove(id) != null;
    }

    /* ── R2O with raw SQL ────────────────────────────────────── */

    public Map<String, Object> runR2oFromSql(String name, String schemaSql, String dataSql) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            SqlInputParser parser = new SqlInputParser();
            R2oRawExporter exporter = new R2oRawExporter();
            SqlInputParser.SqlSchema schema = parser.parseSchema(schemaSql);
            SqlInputParser.SqlData data = parser.parseData(dataSql);
            R2oRawExporter.ExportResult export = exporter.export(schema, data);

            result.put("status", "completed");
            result.put("tables", schema.tables().size());
            result.put("rows", data.rowsByTable().values().stream().mapToInt(List::size).sum());
            result.put("triples", export.model().size());

            // Write output
            Path outDir = Path.of("target", "semantic-output", "r2o", name);
            Files.createDirectories(outDir);
            writeModel(export.model(), outDir.resolve("raw-mapping.ttl"), Lang.TURTLE);

            // Merge into inferred model if pipeline has run
            if (inferredModel != null) {
                synchronized (this) {
                    inferredModel.add(export.model());
                    result.put("mergedIntoGraph", true);
                    result.put("newTotalTriples", inferredModel.size());
                }
            }
            
            // Add to persistent Database
            ontologyDatabase.addOntology(name, export.model());

            // Extract class names for UI display
            List<String> classes = new ArrayList<>();
            export.model().listSubjects().forEachRemaining(r -> {
                if (r.isURIResource()) {
                    String uri = r.getURI();
                    if (uri.contains("#")) classes.add(uri.substring(uri.lastIndexOf('#') + 1));
                }
            });
            result.put("extractedClasses", classes.stream().distinct().limit(20).collect(Collectors.toList()));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /* ── Mapping Suggestions ─────────────────────────────────── */

    public List<Map<String, Object>> getMappingSuggestions() {
        ensurePipeline();
        try {
            String raw = Files.readString(OUTPUT_DIR.resolve("mapping-suggestions.tsv"));
            List<Map<String, Object>> suggestions = new ArrayList<>();
            String[] lines = raw.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].split("\t");
                if (parts.length >= 6) {
                    suggestions.add(Map.of(
                        "ontology", parts[0], "kind", parts[1], "localTerm", parts[2],
                        "suggestedAicteTerm", parts[3], "score", parts[4], "method", parts[5]
                    ));
                }
            }
            return suggestions;
        } catch (IOException e) {
            return List.of();
        }
    }

    /* ── Schema Diff ─────────────────────────────────────────── */

    public Map<String, Object> getOntologyDiff() {
        Model previous = loadModel("semantic/ontologies/central/aicte.ttl");
        Model current = ModelFactory.createDefaultModel().add(previous);
        current.createResource("https://semlink.example.org/aicte#Faculty")
            .addProperty(RDF.type, org.apache.jena.vocabulary.OWL.Class);
        SchemaVersionManager mgr = new SchemaVersionManager();
        OntologyDiff diff = mgr.diff("aicte", previous, current);
        return Map.of("sourceId", diff.sourceId(), "changeType", diff.changeType().name(),
            "addedTriples", diff.addedTriples(), "removedTriples", diff.removedTriples());
    }

    /* ── R2O Onboarding ──────────────────────────────────────── */

    public Map<String, Object> runR2oOnboarding(String exampleName, String mode) {
        R2oWorkflow workflow = new R2oWorkflow(apiKey);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if ("raw".equals(mode)) {
                workflow.raw(exampleName);
                result.put("step", "raw");
            } else {
                workflow.assist(exampleName);
                result.put("step", "assisted");
            }
            result.put("status", "completed");
            result.put("exampleName", exampleName);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /* ── Internal helpers ─────────────────────────────────────── */

    private Map<String, Model> loadOntologyModels() {
        Map<String, Model> models = new LinkedHashMap<>();
        for (String[] res : ONTOLOGY_RESOURCES) {
            models.put(res[0], loadModel(res[1]));
        }
        return models;
    }

    private Model merge(Collection<Model> models) {
        Model merged = ModelFactory.createDefaultModel();
        models.forEach(merged::add);
        return merged;
    }

    private Model createInferredModel(Model base) {
        GenericRuleReasoner reasoner = new GenericRuleReasoner(loadRules());
        reasoner.setMode(GenericRuleReasoner.FORWARD_RETE);
        InfModel inf = ModelFactory.createInfModel(reasoner, base);
        inf.prepare();
        Model mat = ModelFactory.createDefaultModel();
        mat.add(inf);
        return mat;
    }

    private ValidationReport validate(Model model) {
        Model shapes = loadModel(SHAPES_RESOURCE);
        Shapes s = ShaclValidator.get().parse(shapes.getGraph());
        return ShaclValidator.get().validate(s, model.getGraph());
    }

    private Model loadModel(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Missing: " + resource);
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, in, org.apache.jena.riot.Lang.TURTLE);
            return model;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resource, e);
        }
    }

    private List<Rule> loadRules() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RULE_RESOURCE)) {
            if (in == null) throw new IllegalArgumentException("Missing rules: " + RULE_RESOURCE);
            String rulesStr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Rule.parseRules(rulesStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int countResources(String classUri) {
        if (inferredModel == null) return 0;
        Set<String> uris = new HashSet<>();
        ResIterator it = inferredModel.listResourcesWithProperty(RDF.type, inferredModel.createResource(classUri));
        try { it.forEachRemaining(r -> { if (r.isURIResource()) uris.add(r.getURI()); }); }
        finally { it.close(); }
        return uris.size();
    }

    private void writeModel(Model m, Path p, Lang l) throws IOException {
        Files.createDirectories(p.getParent());
        try (OutputStream os = Files.newOutputStream(p)) { RDFDataMgr.write(os, m, l); }
    }

    private void writeString(Path p, String c) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, c, StandardCharsets.UTF_8);
    }
}
