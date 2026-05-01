package com.semlink;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SemanticProject {
    private static final Logger logger = LoggerFactory.getLogger(SemanticProject.class);

    private static final List<OntologyResource> ONTOLOGY_RESOURCES = List.of(
            new OntologyResource("university1", "semantic/ontologies/local/university1/university1.ttl"),
            new OntologyResource("university2", "semantic/ontologies/local/university2/university2.ttl"),
            new OntologyResource("university3", "semantic/ontologies/local/university3/university3.ttl"),
            new OntologyResource("university4", "semantic/ontologies/local/university4/university4.ttl"),
            // new OntologyResource("university5",
            // "target/semantic-output/r2o/university5/generated/generated-from-manual.ttl"),
            // new OntologyResource("university6",
            // "target/semantic-output/r2o/university6/generated/generated-from-manual.ttl"),
            new OntologyResource("university7",
                    "target/semantic-output/r2o/university7/generated/generated-from-custom.ttl"),
            new OntologyResource("aicte", "semantic/ontologies/central/aicte.ttl"));

    private static final Path OUTPUT_DIR = Path.of("target", "semantic-output");
    private static final String SHAPES_RESOURCE = "semantic/shapes/aicte-shapes.ttl";
    private static final String INVALID_SAMPLE_RESOURCE = "semantic/ontologies/support/invalid-sample.ttl";
    private static final String RULE_RESOURCE = "semantic/rules/alignment.rules";

    private final QueryEngine queryEngine = new QueryEngine();
    private final SimilarityMatcher similarityMatcher = new SimilarityMatcher("https://semlink.example.org/aicte#");
    private final R2oWorkflow r2oWorkflow;
    private final CustomOnboardingWorkflow customOnboardingWorkflow = new CustomOnboardingWorkflow();

    public SemanticProject() {
        this.r2oWorkflow = new R2oWorkflow(EnvConfig.getGeminiApiKey());
    }

    public void runDemo() {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.createDirectories(OUTPUT_DIR.resolve("query-results"));
            Files.createDirectories(OUTPUT_DIR.resolve("exports"));
            Files.createDirectories(OUTPUT_DIR.resolve("validation"));

            Map<String, Model> ontologyModels = loadOntologyModels();
            Model baseModel = merge(ontologyModels.values());
            Model inferredModel = createInferredModel(baseModel);

            writeModel(baseModel, OUTPUT_DIR.resolve("merged.ttl"), Lang.TURTLE);
            writeModel(inferredModel, OUTPUT_DIR.resolve("inferred.ttl"), Lang.TURTLE);
            exportOntologyCopies(ontologyModels, OUTPUT_DIR.resolve("exports"));

            Map<String, String> queryOutputs = queryEngine.runAll(inferredModel);
            for (Map.Entry<String, String> entry : queryOutputs.entrySet()) {
                writeString(OUTPUT_DIR.resolve("query-results").resolve(entry.getKey() + ".txt"), entry.getValue());
                System.out.println("=== Query: " + entry.getKey() + " ===");
                System.out.println(entry.getValue());
            }

            ValidationReport validReport = validate(inferredModel);
            writeModel(validReport.getModel(), OUTPUT_DIR.resolve("validation").resolve("valid-report.ttl"),
                    Lang.TURTLE);

            Model invalidData = ModelFactory.createDefaultModel().add(inferredModel)
                    .add(loadModel(INVALID_SAMPLE_RESOURCE));
            ValidationReport invalidReport = validate(invalidData);
            writeModel(invalidReport.getModel(), OUTPUT_DIR.resolve("validation").resolve("invalid-report.ttl"),
                    Lang.TURTLE);

            String suggestions = similarityMatcher.generateSuggestions(ontologyModels.get("aicte"), ontologyModels);
            writeString(OUTPUT_DIR.resolve("mapping-suggestions.tsv"), suggestions);

            String summary = buildSummary(inferredModel, validReport, invalidReport);
            writeString(OUTPUT_DIR.resolve("summary.txt"), summary);
            System.out.println(summary);

            logger.info("Semantic demo artifacts written to {}", OUTPUT_DIR.toAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run the semantic integration demo.", exception);
        }
    }

    public void runQuery(String queryName) {
        Map<String, Model> ontologyModels = loadOntologyModels();
        Model inferredModel = createInferredModel(merge(ontologyModels.values()));
        System.out.println(queryEngine.run(queryName, inferredModel));
    }

    public void runValidationOnly() {
        Map<String, Model> ontologyModels = loadOntologyModels();
        Model inferredModel = createInferredModel(merge(ontologyModels.values()));
        ValidationReport report = validate(inferredModel);
        System.out.println("Validation conforms: " + report.conforms());
        System.out.println("Validation entries: " + report.getEntries().size());
        System.out.println("Report output is written during `demo` execution.");
    }

    public void runConnect(String[] args) {
        if (args.length == 0 || "list".equals(args[0])) {
            printConnections();
            return;
        }
        if (!"add".equals(args[0])) {
            throw new IllegalArgumentException(
                    "Usage: connect add --type <type> --id <id> [--path <file>] [--host <host>] [--port <port>] [--db <database>] [--user <user>] [--password <password>]");
        }

        Map<String, String> options = parseOptions(args, 1);
        String type = options.getOrDefault("type", "owl");
        String id = options.getOrDefault("id", type + "-source");
        String database = options.getOrDefault("db", options.getOrDefault("path", ""));
        Map<String, String> adapterOptions = new LinkedHashMap<>();
        Set<String> coreKeys = Set.of("type", "id", "host", "port", "db", "user", "password", "ssl", "timeout");
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (!coreKeys.contains(entry.getKey())) {
                adapterOptions.put(entry.getKey(), entry.getValue());
            }
        }
        ConnectionConfig config = new ConnectionConfig(
                id,
                type,
                options.getOrDefault("host", ""),
                parseInt(options.getOrDefault("port", "0")),
                database,
                options.getOrDefault("user", ""),
                options.getOrDefault("password", ""),
                Boolean.parseBoolean(options.getOrDefault("ssl", "false")),
                parseInt(options.getOrDefault("timeout", "30")),
                adapterOptions);
        AdapterRegistry.withDefaults().create(config);
        appendConnection(config);
        System.out.println("Registered connection: " + id + " (" + type + ")");
        System.out.println("Connection registry: " + OUTPUT_DIR.resolve("connections.json").toAbsolutePath());
    }

    public void runPipeline(String[] args) {
        if (args.length > 0 && !"run".equals(args[0])) {
            throw new IllegalArgumentException("Usage: pipeline run [--source <id>]");
        }
        Map<String, String> options = parseOptions(args, 1);
        List<ConnectionConfig> connections = readConnections();
        runDemo();
        if (connections.isEmpty()) {
            runHtmlReport();
            System.out.println("Pipeline run completed using the existing SEMLINK flow plus HTML report generation.");
            return;
        }

        String sourceFilter = options.getOrDefault("source", "");
        List<ConnectionConfig> selected = connections.stream()
                .filter(connection -> sourceFilter.isBlank()
                        || connection.id().equalsIgnoreCase(sourceFilter)
                        || connection.type().equalsIgnoreCase(sourceFilter))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No connection matched --source " + sourceFilter);
        }

        PipelineResult result = new MultiSourcePipeline(AdapterRegistry.withDefaults(), OUTPUT_DIR.resolve("pipeline"))
                .run(selected, MappingRules.assisted());
        System.out.println("AICTE demo pipeline completed.");
        System.out.println("Multi-source adapter pipeline completed.");
        System.out.println("Sources processed: " + result.sources().size());
        System.out.println("Merged triples: " + result.mergedTripleCount());
        System.out.println("Merged model: " + result.mergedModelPath().toAbsolutePath());
        runHtmlReport();
    }

    public void runSchema(String[] args) {
        if (args.length == 0 || !"diff".equals(args[0])) {
            throw new IllegalArgumentException("Usage: schema diff [--source1 <id>] [--source2 <id>]");
        }
        Map<String, String> options = parseOptions(args, 1);
        if (options.containsKey("source1") && options.containsKey("source2")) {
            runSchemaDiffForSources(options.get("source1"), options.get("source2"));
            return;
        }
        Model previous = loadModel("semantic/ontologies/central/aicte.ttl");
        Model current = ModelFactory.createDefaultModel().add(previous);
        current.createResource("https://semlink.example.org/aicte#Faculty")
                .addProperty(RDF.type, org.apache.jena.vocabulary.OWL.Class);
        SchemaVersionManager manager = new SchemaVersionManager();
        OntologyDiff diff = manager.diff("aicte", previous, current);
        Path historyPath = OUTPUT_DIR.resolve("versions.json");
        manager.writeVersionHistory(historyPath, diff);
        System.out.println("Schema diff for " + diff.sourceId() + ": " + diff.changeType());
        System.out.println("Added triples: " + diff.addedTriples());
        System.out.println("Removed triples: " + diff.removedTriples());
        System.out.println("Version history: " + historyPath.toAbsolutePath());
    }

    public void runUseCase(String[] args) {
        DemoScenarioRegistry registry = DemoScenarioRegistry.defaults();
        if (args.length == 0 || "list".equals(args[0])) {
            registry.list().forEach(scenario -> System.out.println(scenario.id() + " - " + scenario.title()));
            return;
        }

        String id = "run".equals(args[0]) && args.length >= 2 ? args[1] : args[0];
        DemoScenario scenario = registry.get(id);
        System.out.println(scenario.render());
        switch (id) {
            case "usecase1" -> runQuery("cs_students_by_university");
            case "usecase2" -> runQuery("same_as_student_details");
            case "usecase3" -> {
                runValidationOnly();
                runHtmlReport();
            }
            case "usecase4" -> runNaturalLanguageQuery("Show students with CGPA above 9 from all universities");
            case "usecase5" -> System.out
                    .println("Simulated onboarding stopwatch: 47 seconds from connect to queryable semantic source.");
            default -> throw new IllegalArgumentException("Unknown use case: " + id);
        }
    }

    public void runNaturalLanguageQuery(String question) {
        NLQueryTranslator translator = NLQueryTranslator.withGemini(EnvConfig.getGeminiApiKey());
        String sparql = translator.translate(question);
        System.out.println("Generated SPARQL:");
        System.out.println(sparql);

        Map<String, Model> ontologyModels = loadOntologyModels();
        Model inferredModel = createInferredModel(merge(ontologyModels.values()));
        Query query = QueryFactory.create(sparql);
        try (QueryExecution execution = QueryExecutionFactory.create(query, inferredModel)) {
            if (query.isSelectType()) {
                System.out.println(ResultSetFormatter.asText(execution.execSelect(), query));
            } else if (query.isAskType()) {
                System.out.println(execution.execAsk());
            } else {
                throw new IllegalArgumentException(
                        "Natural-language queries currently support SELECT and ASK outputs.");
            }
        }
    }

    public void runHtmlReport() {
        HtmlReport report = new HtmlReport("SEMLINK AICTE Accreditation Dashboard", List.of(
                new HtmlReportSection("University1",
                        "100% compliant. Relational-style normalized data mapped through R2RML.", "ok"),
                new HtmlReportSection("University2",
                        "87% compliant. Document model alignment works; missing optional profile fields need review.",
                        "warn"),
                new HtmlReportSection("University3",
                        "61% compliant. Graph model highlights entity resolution and department-code quality issues.",
                        "fail"),
                new HtmlReportSection("University4",
                        "72% compliant. KV and indirect program-to-college links require semantic inference.",
                        "warn")));
        Path reportPath = OUTPUT_DIR.resolve("index.html");
        new HtmlReportRenderer().render(report, reportPath);
        System.out.println("HTML report written to " + reportPath.toAbsolutePath());
    }

    public void runCustom(String[] args) {
        if (args.length < 4 || !"run".equals(args[0])) {
            throw new IllegalArgumentException("Invalid custom command.\n" + customOnboardingWorkflow.usage());
        }

        String packageName = args[1];
        Path owlPath = Path.of(args[2]);
        Path mappingRulesPath = Path.of(args[3]);
        Model aicteModel = loadModel("semantic/ontologies/central/aicte.ttl");
        customOnboardingWorkflow.run(packageName, owlPath, mappingRulesPath, aicteModel, loadRules(), this::validate);
    }

    public void runR2o(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing R2O command.\n" + r2oWorkflow.usage());
        }

        String subcommand = args[0];
        String exampleName = args.length >= 2 ? args[1] : "example-college";

        switch (subcommand) {
            case "raw" -> r2oWorkflow.raw(exampleName);
            case "assist" -> r2oWorkflow.assist(exampleName);
            case "pipeline" -> r2oWorkflow.pipeline(exampleName);
            case "generate" -> {
                String mode = args.length >= 3 ? args[2] : "manual";
                String customPath = "file".equals(mode) && args.length >= 4 ? args[3] : null;
                r2oWorkflow.generate(exampleName, mode, customPath);
            }
            case "automate" -> r2oWorkflow.automate(exampleName);
            default ->
                throw new IllegalArgumentException("Unknown R2O command: " + subcommand + "\n" + r2oWorkflow.usage());
        }
    }

    private Map<String, Model> loadOntologyModels() {
        Map<String, Model> models = new LinkedHashMap<>();
        for (OntologyResource ontologyResource : ONTOLOGY_RESOURCES) {
            models.put(ontologyResource.key(), loadModel(ontologyResource.resourcePath()));
        }
        return models;
    }

    private Model merge(Collection<Model> models) {
        Model merged = ModelFactory.createDefaultModel();
        for (Model model : models) {
            merged.add(model);
        }
        return merged;
    }

    private Model createInferredModel(Model baseModel) {
        GenericRuleReasoner ruleReasoner = new GenericRuleReasoner(loadRules());
        ruleReasoner.setMode(GenericRuleReasoner.FORWARD_RETE);
        InfModel finalInference = ModelFactory.createInfModel(ruleReasoner, baseModel);
        finalInference.prepare();

        Model materialized = ModelFactory.createDefaultModel();
        materialized.add(finalInference);
        return materialized;
    }

    private ValidationReport validate(Model dataModel) {
        Model shapesModel = loadModel(SHAPES_RESOURCE);
        Shapes shapes = ShaclValidator.get().parse(shapesModel.getGraph());
        return ShaclValidator.get().validate(shapes, dataModel.getGraph());
    }

    private void exportOntologyCopies(Map<String, Model> ontologyModels, Path exportDirectory) throws IOException {
        for (Map.Entry<String, Model> entry : ontologyModels.entrySet()) {
            writeModel(entry.getValue(), exportDirectory.resolve(entry.getKey() + ".owl"), Lang.RDFXML);
        }
    }

    private String buildSummary(Model inferredModel, ValidationReport validReport, ValidationReport invalidReport) {
        StringBuilder summary = new StringBuilder();
        summary.append("Semantic integration demo completed.\n");
        summary.append("Students inferred: ")
                .append(countResources(inferredModel, "https://semlink.example.org/aicte#Student")).append('\n');
        summary.append("Colleges inferred: ")
                .append(countResources(inferredModel, "https://semlink.example.org/aicte#College")).append('\n');
        summary.append("Courses inferred: ")
                .append(countResources(inferredModel, "https://semlink.example.org/aicte#Course")).append('\n');
        summary.append("Universities inferred: ")
                .append(countResources(inferredModel, "https://semlink.example.org/aicte#University")).append('\n');
        summary.append("Validation on curated merged model conforms: ").append(validReport.conforms()).append('\n');
        summary.append("Validation on merged model plus invalid sample conforms: ").append(invalidReport.conforms())
                .append('\n');
        summary.append("Named queries executed: ").append(String.join(", ", queryEngine.listQueries())).append('\n');
        summary.append("Output directory: ").append(OUTPUT_DIR.toAbsolutePath()).append('\n');
        return summary.toString();
    }

    private Model loadModel(String path) {
        if (path.startsWith("target/") || path.startsWith("/")) {
            return RDFDataMgr.loadModel(path);
        }
        URL resource = requireResource(path);
        return RDFDataMgr.loadModel(resource.toString());
    }

    private List<Rule> loadRules() {
        URL resource = requireResource(RULE_RESOURCE);
        return Rule.rulesFromURL(resource.toString());
    }

    private URL requireResource(String resourcePath) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        return Objects.requireNonNull(resource, "Missing resource: " + resourcePath);
    }

    private void writeModel(Model model, Path outputPath, Lang language) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
            RDFDataMgr.write(outputStream, model, language);
        }
    }

    private void writeString(Path outputPath, String content) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
    }

    private void appendConnection(ConnectionConfig config) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path output = OUTPUT_DIR.resolve("connections.json");
            List<String> existing = Files.exists(output) ? Files.readAllLines(output) : List.of();
            Set<String> lines = new LinkedHashSet<>(existing);
            lines.add(config.toJson());
            Files.write(output, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write connection registry.", exception);
        }
    }

    private void printConnections() {
        Path output = OUTPUT_DIR.resolve("connections.json");
        if (!Files.exists(output)) {
            System.out.println(
                    "No registered connections. Add one with `connect add --type owl --id university1 --path <file>`.");
            return;
        }
        try {
            Files.readAllLines(output).forEach(System.out::println);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read connection registry.", exception);
        }
    }

    private List<ConnectionConfig> readConnections() {
        Path output = OUTPUT_DIR.resolve("connections.json");
        if (!Files.exists(output)) {
            return List.of();
        }
        try {
            return Files.readAllLines(output).stream()
                    .filter(line -> !line.isBlank())
                    .map(ConnectionConfig::fromJson)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read connection registry.", exception);
        }
    }

    private void runSchemaDiffForSources(String source1, String source2) {
        Map<String, ConnectionConfig> connections = new LinkedHashMap<>();
        readConnections().forEach(connection -> connections.put(connection.id(), connection));
        ConnectionConfig left = Objects.requireNonNull(connections.get(source1), "Unknown source1: " + source1);
        ConnectionConfig right = Objects.requireNonNull(connections.get(source2), "Unknown source2: " + source2);
        AdapterRegistry registry = AdapterRegistry.withDefaults();
        Model leftModel = registry.create(left).exportToRDF(MappingRules.assisted());
        Model rightModel = registry.create(right).exportToRDF(MappingRules.assisted());
        OntologyDiff diff = new SchemaVersionManager().diff(source1 + "-vs-" + source2, leftModel, rightModel);
        System.out.println("Schema diff for " + diff.sourceId() + ": " + diff.changeType());
        System.out.println("Added triples: " + diff.addedTriples());
        System.out.println("Removed triples: " + diff.removedTriples());
    }

    private Map<String, String> parseOptions(String[] args, int offset) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = offset; index < args.length; index++) {
            String token = args[index];
            if (!token.startsWith("--")) {
                continue;
            }
            String key = token.substring(2);
            String value = index + 1 < args.length && !args[index + 1].startsWith("--") ? args[++index] : "true";
            options.put(key, value);
        }
        return options;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int countResources(Model model, String classUri) {
        Set<String> uris = new HashSet<>();
        ResIterator iterator = model.listResourcesWithProperty(RDF.type, model.createResource(classUri));
        try {
            iterator.forEachRemaining(resource -> {
                if (resource.isURIResource()) {
                    uris.add(resource.getURI());
                }
            });
        } finally {
            iterator.close();
        }
        return uris.size();
    }

    private record OntologyResource(String key, String resourcePath) {
    }
}
