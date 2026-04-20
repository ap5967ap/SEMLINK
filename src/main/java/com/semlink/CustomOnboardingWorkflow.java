package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ValidationReport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomOnboardingWorkflow {
    private static final Path OUTPUT_ROOT = Path.of("target", "semantic-output", "custom");

    private final QueryEngine queryEngine = new QueryEngine();

    public void run(String packageName,
                    Path owlPath,
                    Path mappingRulesPath,
                    Model aicteModel,
                    List<Rule> baseRules,
                    java.util.function.Function<Model, ValidationReport> validator) {
        Model collegeModel = loadModel(owlPath);
        List<Rule> customRules = Rule.rulesFromURL(mappingRulesPath.toUri().toString());

        Model merged = ModelFactory.createDefaultModel();
        merged.add(aicteModel);
        merged.add(collegeModel);

        Model inferred = createInferredModel(merged, baseRules, customRules);
        ValidationReport validationReport = validator.apply(inferred);

        Path outputDir = OUTPUT_ROOT.resolve(packageName);
        try {
            Files.createDirectories(outputDir.resolve("query-results"));
            Files.createDirectories(outputDir.resolve("validation"));

            writeModel(collegeModel, outputDir.resolve("college-input.ttl"), Lang.TURTLE);
            Files.copy(mappingRulesPath, outputDir.resolve("mapping-rules.rules"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            writeModel(merged, outputDir.resolve("merged.ttl"), Lang.TURTLE);
            writeModel(inferred, outputDir.resolve("inferred.ttl"), Lang.TURTLE);
            writeModel(validationReport.getModel(), outputDir.resolve("validation").resolve("report.ttl"), Lang.TURTLE);

            Map<String, String> queryOutputs = queryEngine.runAll(inferred);
            for (Map.Entry<String, String> entry : queryOutputs.entrySet()) {
                writeString(outputDir.resolve("query-results").resolve(entry.getKey() + ".txt"), entry.getValue());
            }

            String summary = buildSummary(packageName, owlPath, mappingRulesPath, validationReport, queryOutputs.size(), outputDir);
            writeString(outputDir.resolve("summary.txt"), summary);
            System.out.println(summary);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run custom onboarding workflow.", exception);
        }
    }

    public String usage() {
        return String.join("\n",
            "  mvn exec:java -Dexec.args=\"custom run college-pack /abs/path/college.owl /abs/path/mapping-rules.rules\"",
            "  mvn exec:java -Dexec.args=\"custom run college-pack src/main/resources/semantic/onboarding/custom-sample/college.owl src/main/resources/semantic/onboarding/custom-sample/mapping-rules.rules\""
        );
    }

    private Model loadModel(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Missing input file: " + path.toAbsolutePath());
        }
        return RDFDataMgr.loadModel(path.toUri().toString());
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

    private Model createInferredModel(Model baseModel, List<Rule> baseRules, List<Rule> customRules) {
        List<Rule> combinedRules = new ArrayList<>(baseRules);
        combinedRules.addAll(customRules);
        GenericRuleReasoner ruleReasoner = new GenericRuleReasoner(combinedRules);
        ruleReasoner.setMode(GenericRuleReasoner.FORWARD_RETE);
        org.apache.jena.rdf.model.InfModel infModel = ModelFactory.createInfModel(ruleReasoner, baseModel);
        infModel.prepare();
        Model materialized = ModelFactory.createDefaultModel();
        materialized.add(infModel);
        return materialized;
    }

    private String buildSummary(String packageName,
                                Path owlPath,
                                Path mappingRulesPath,
                                ValidationReport validationReport,
                                int queryCount,
                                Path outputDir) {
        StringBuilder summary = new StringBuilder();
        summary.append("Custom onboarding workflow completed.\n");
        summary.append("Package: ").append(packageName).append('\n');
        summary.append("College OWL: ").append(owlPath.toAbsolutePath()).append('\n');
        summary.append("Mapping rules: ").append(mappingRulesPath.toAbsolutePath()).append('\n');
        summary.append("Validation conforms: ").append(validationReport.conforms()).append('\n');
        summary.append("Queries executed: ").append(queryCount).append('\n');
        summary.append("Output directory: ").append(outputDir.toAbsolutePath()).append('\n');
        return summary.toString();
    }
}
