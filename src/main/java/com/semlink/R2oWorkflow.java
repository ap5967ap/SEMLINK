package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class R2oWorkflow {
    private static final Path OUTPUT_ROOT = Path.of("target", "semantic-output", "r2o");

    private final SqlInputParser sqlInputParser = new SqlInputParser();
    private final R2oAssistant assistant = new R2oAssistant();
    private final R2rmlRenderer renderer = new R2rmlRenderer();

    public void assist(String exampleName) {
        String schemaSql = readResource(resource(exampleName, "schema.sql"));
        SqlInputParser.SqlSchema schema = sqlInputParser.parseSchema(schemaSql);
        R2oAssistant.DraftResult draft = assistant.buildDraft(exampleName, schema);

        Path outputDir = assistedDirectory(exampleName);
        writeString(outputDir.resolve("draft-r2rml-mapping.ttl"), draft.draftMapping());
        Path refinedPath = outputDir.resolve("refined-r2rml-mapping.ttl");
        if (Files.notExists(refinedPath)) {
            writeString(refinedPath, draft.draftMapping());
        }
        writeString(outputDir.resolve("review-report.md"), draft.reviewReport());
        writeString(outputDir.resolve("schema-profile.tsv"), draft.schemaProfile());

        System.out.println("Assisted R2O draft generated.");
        System.out.println("Review files written to: " + outputDir.toAbsolutePath());
        if (Files.exists(refinedPath)) {
            System.out.println("Refined mapping file preserved at: " + refinedPath.toAbsolutePath());
        }
        System.out.println("Next step: edit refined-r2rml-mapping.ttl and run:");
        System.out.println("  mvn -q exec:java -Dexec.args=\"r2o generate " + exampleName + " refined\"");
    }

    public void pipeline(String exampleName) {
        assist(exampleName);
        generate(exampleName, "draft", null);
    }

    public void generate(String exampleName, String mode, String customPath) {
        String dataSql = readResource(resource(exampleName, "sample-data.sql"));
        MappingSelection selection = resolveMappingSelection(exampleName, mode, customPath);
        R2rmlRenderer.RenderResult result = renderer.render(selection.mappingContent(), dataSql);

        Path outputDir = generatedDirectory(exampleName);
        String outputFile = switch (selection.label()) {
            case "manual" -> "generated-from-manual.ttl";
            case "draft" -> "generated-from-draft.ttl";
            case "refined" -> "generated-from-refined.ttl";
            default -> "generated-from-custom.ttl";
        };

        writeModel(result.model(), outputDir.resolve(outputFile), Lang.TURTLE);

        StringBuilder summary = new StringBuilder();
        summary.append("R2O generation completed.\n");
        summary.append("Mode: ").append(selection.label()).append('\n');
        summary.append("Mapping source: ").append(selection.sourceDescription()).append('\n');
        summary.append("Input rows processed: ").append(result.inputRowCount()).append('\n');
        summary.append("Triples maps applied: ").append(result.triplesMapCount()).append('\n');
        summary.append("Triples generated: ").append(result.tripleCount()).append('\n');
        summary.append("Output file: ").append(outputDir.resolve(outputFile).toAbsolutePath()).append('\n');

        writeString(outputDir.resolve("last-run-summary.txt"), summary.toString());
        System.out.println(summary);
    }

    public String usage() {
        return String.join("\n",
            "  mvn exec:java -Dexec.args=\"r2o assist example-college\"",
            "  mvn exec:java -Dexec.args=\"r2o pipeline example-college\"",
            "  mvn exec:java -Dexec.args=\"r2o generate example-college manual\"",
            "  mvn exec:java -Dexec.args=\"r2o generate example-college draft\"",
            "  mvn exec:java -Dexec.args=\"r2o generate example-college refined\"",
            "  mvn exec:java -Dexec.args=\"r2o generate example-college file target/semantic-output/r2o/example-college/assisted/refined-r2rml-mapping.ttl\""
        );
    }

    private MappingSelection resolveMappingSelection(String exampleName, String mode, String customPath) {
        return switch (mode) {
            case "manual" -> new MappingSelection(
                "manual",
                resource(exampleName, "r2rml-mapping.ttl"),
                readResource(resource(exampleName, "r2rml-mapping.ttl"))
            );
            case "draft" -> {
                Path draftPath = assistedDirectory(exampleName).resolve("draft-r2rml-mapping.ttl");
                yield new MappingSelection("draft", draftPath.toString(), readFile(draftPath));
            }
            case "refined" -> {
                Path refinedPath = assistedDirectory(exampleName).resolve("refined-r2rml-mapping.ttl");
                yield new MappingSelection("refined", refinedPath.toString(), readFile(refinedPath));
            }
            case "file" -> {
                if (customPath == null || customPath.isBlank()) {
                    throw new IllegalArgumentException("Custom file mode requires a mapping path.");
                }
                Path path = Path.of(customPath);
                yield new MappingSelection("custom-file", path.toString(), readFile(path));
            }
            default -> throw new IllegalArgumentException("Unknown R2O generate mode: " + mode);
        };
    }

    private String resource(String exampleName, String fileName) {
        return "semantic/r2o/" + exampleName + "/" + fileName;
    }

    private Path assistedDirectory(String exampleName) {
        return OUTPUT_ROOT.resolve(exampleName).resolve("assisted");
    }

    private Path generatedDirectory(String exampleName) {
        return OUTPUT_ROOT.resolve(exampleName).resolve("generated");
    }

    private String readResource(String resourcePath) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        URL required = Objects.requireNonNull(resource, "Missing resource: " + resourcePath);
        try {
            return new String(required.openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, exception);
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read mapping file: " + path, exception);
        }
    }

    private void writeModel(Model model, Path outputPath, Lang language) {
        try {
            Files.createDirectories(outputPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                RDFDataMgr.write(outputStream, model, language);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write model to " + outputPath, exception);
        }
    }

    private void writeString(Path outputPath, String content) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write text file: " + outputPath, exception);
        }
    }

    private record MappingSelection(String label, String sourceDescription, String mappingContent) {
    }
}
