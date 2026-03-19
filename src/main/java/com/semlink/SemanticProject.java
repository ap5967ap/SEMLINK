package com.semlink;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SemanticProject {
    private static final Logger logger = LoggerFactory.getLogger(SemanticProject.class);

    private static final List<OntologyResource> ONTOLOGY_RESOURCES = List.of(
        new OntologyResource("university1", "db/college1/college_populated.ttl"),
        new OntologyResource("university2", "semantic/ontologies/university2.ttl"),
        new OntologyResource("university3", "semantic/ontologies/university3.ttl"),
        new OntologyResource("university4", "semantic/ontologies/university4.ttl"),
        new OntologyResource("aicte", "semantic/ontologies/aicte.ttl")
    );

    private static final Path OUTPUT_DIR = Path.of("target", "semantic-output");
    private static final String SHAPES_RESOURCE = "semantic/shapes/aicte-shapes.ttl";
    private static final String INVALID_SAMPLE_RESOURCE = "semantic/ontologies/invalid-sample.ttl";
    private static final String RULE_RESOURCE = "semantic/rules/inference.rules";

    private final QueryEngine queryEngine = new QueryEngine("semantic/queries");
    private final SimilarityMatcher similarityMatcher = new SimilarityMatcher("https://semlink.example.org/aicte#");

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
            writeModel(validReport.getModel(), OUTPUT_DIR.resolve("validation").resolve("valid-report.ttl"), Lang.TURTLE);

            Model invalidData = ModelFactory.createDefaultModel().add(inferredModel).add(loadModel(INVALID_SAMPLE_RESOURCE));
            ValidationReport invalidReport = validate(invalidData);
            writeModel(invalidReport.getModel(), OUTPUT_DIR.resolve("validation").resolve("invalid-report.ttl"), Lang.TURTLE);

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
        summary.append("Students inferred: ").append(countResources(inferredModel, "https://semlink.example.org/aicte#Student")).append('\n');
        summary.append("Colleges inferred: ").append(countResources(inferredModel, "https://semlink.example.org/aicte#College")).append('\n');
        summary.append("Courses inferred: ").append(countResources(inferredModel, "https://semlink.example.org/aicte#Course")).append('\n');
        summary.append("Universities inferred: ").append(countResources(inferredModel, "https://semlink.example.org/aicte#University")).append('\n');
        summary.append("Validation on curated merged model conforms: ").append(validReport.conforms()).append('\n');
        summary.append("Validation on merged model plus invalid sample conforms: ").append(invalidReport.conforms()).append('\n');
        summary.append("Named queries executed: ").append(String.join(", ", queryEngine.listQueries())).append('\n');
        summary.append("Output directory: ").append(OUTPUT_DIR.toAbsolutePath()).append('\n');
        return summary.toString();
    }

    private Model loadModel(String resourcePath) {
        URL resource = requireResource(resourcePath);
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
