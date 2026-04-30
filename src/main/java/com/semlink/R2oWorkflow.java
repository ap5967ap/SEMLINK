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
    private final R2oRawExporter rawExporter = new R2oRawExporter();
    private final R2oAssistant assistant = new R2oAssistant();
    private final R2rmlRenderer renderer = new R2rmlRenderer();
    private final com.semlink.onboarding.LLMMappingService llmService;

    public R2oWorkflow(String geminiApiKey) {
        this.llmService = new com.semlink.onboarding.LLMMappingService(geminiApiKey);
    }

    public void raw(String exampleName) {
        String schemaSql = readResource(resource(exampleName, "schema.sql"));
        String dataSql = readResource(resource(exampleName, "sample-data.sql"));

        SqlInputParser.SqlSchema schema = sqlInputParser.parseSchema(schemaSql);
        SqlInputParser.SqlData data = sqlInputParser.parseData(dataSql);
        R2oRawExporter.ExportResult exportResult = rawExporter.export(schema, data);

        Path outputDir = rawDirectory(exampleName);
        Path outputPath = outputDir.resolve("raw-direct-mapping.ttl");
        writeModel(exportResult.model(), outputPath, Lang.TURTLE);

        StringBuilder summary = new StringBuilder();
        summary.append("Raw RDF export completed.\n");
        summary.append("Mode: direct relational projection\n");
        summary.append("Input rows processed: ").append(countRows(data)).append('\n');
        summary.append("Tables exported: ").append(schema.tables().size()).append('\n');
        summary.append("Triples generated: ").append(exportResult.model().size()).append('\n');
        summary.append("Output file: ").append(outputPath.toAbsolutePath()).append('\n');
        writeString(outputDir.resolve("summary.txt"), summary.toString());
        System.out.println(summary);
    }

    public void assist(String exampleName) {
        String schemaSql = readResource(resource(exampleName, "schema.sql"));
        String dataSql = readResource(resource(exampleName, "sample-data.sql"));
        SqlInputParser.SqlSchema schema = sqlInputParser.parseSchema(schemaSql);

        SqlInputParser.SqlData data = sqlInputParser.parseData(dataSql);
        R2oRawExporter.ExportResult rawExport = rawExporter.export(schema, data);
        R2oAssistant.DraftResult draft = assistant.refine(exampleName, schema, rawExport.model(), rawExport.primaryKeys());

        Path outputDir = assistedDirectory(exampleName);
        writeModel(rawExport.model(), rawDirectory(exampleName).resolve("raw-direct-mapping.ttl"), Lang.TURTLE);
        Path refinedPath = outputDir.resolve("refined-from-raw.ttl");
        writeModel(draft.refinedModel(), refinedPath, Lang.TURTLE);
        writeString(outputDir.resolve("review-report.md"), draft.reviewReport());
        writeString(outputDir.resolve("schema-profile.tsv"), draft.schemaProfile());

        StringBuilder summary = new StringBuilder();
        summary.append("Assisted raw-RDF refinement completed.\n");
        summary.append("Raw RDF file: ").append(rawDirectory(exampleName).resolve("raw-direct-mapping.ttl").toAbsolutePath()).append('\n');
        summary.append("Refined AICTE-ready file: ").append(refinedPath.toAbsolutePath()).append('\n');
        summary.append("Input rows processed: ").append(countRows(data)).append('\n');
        summary.append("Raw triples generated: ").append(rawExport.model().size()).append('\n');
        summary.append("Refined triples generated: ").append(draft.refinedModel().size()).append('\n');
        writeString(outputDir.resolve("summary.txt"), summary.toString());
        System.out.println(summary);
    }

    public void pipeline(String exampleName) {
        assist(exampleName);
    }

    public void automate(String exampleName) {
        System.out.println("Automating R2RML generation for: " + exampleName);
        String schemaSql = readResource(resource(exampleName, "schema.sql"));
        String dataSql = readResource(resource(exampleName, "sample-data.sql"));
        SqlInputParser.SchemaStatistics stats = sqlInputParser.buildSchemaStatistics(schemaSql, dataSql);

        String r2rml = llmService.generateR2rml(stats, exampleName);
        if (r2rml == null || r2rml.isBlank()) {
            throw new IllegalStateException("Gemini failed to generate R2RML mapping.");
        }

        Path mappingPath = Path.of("mappings", exampleName, "r2rml-mapping.ttl");
        try {
            Files.createDirectories(mappingPath.getParent());
            Files.writeString(mappingPath, r2rml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save automated mapping to: " + mappingPath, e);
        }

        System.out.println("AI-Generated R2RML mapping saved to: " + mappingPath.toAbsolutePath());
        System.out.println("You can now run: mvn exec:java -Dexec.args=\"r2o generate " + exampleName + " file " + mappingPath + "\"");
    }

    public void generate(String exampleName, String mode, String customPath) {
        String dataSql = readResource(resource(exampleName, "sample-data.sql"));
        MappingSelection selection = resolveMappingSelection(exampleName, mode, customPath);
        R2rmlRenderer.RenderResult result = renderer.render(selection.mappingContent(), dataSql);

        Path outputDir = generatedDirectory(exampleName);
        String outputFile = switch (selection.label()) {
            case "manual" -> "generated-from-manual.ttl";
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
            "  mvn exec:java -Dexec.args=\"r2o raw example-college\"",
            "  mvn exec:java -Dexec.args=\"r2o assist example-college\"",
            "  mvn exec:java -Dexec.args=\"r2o automate example-college\"",
            "  mvn exec:java -Dexec.args=\"r2o pipeline example-college\"",
            "  mvn exec:java -Dexec.args=\"r2o generate example-college manual\"",
            "  mvn exec:java -Dexec.args=\"r2o generate example-college file src/main/resources/semantic/r2o/example-college/r2rml-mapping.ttl\""
        );
    }

    private MappingSelection resolveMappingSelection(String exampleName, String mode, String customPath) {
        return switch (mode) {
            case "manual" -> new MappingSelection(
                "manual",
                resource(exampleName, "r2rml-mapping.ttl"),
                readResource(resource(exampleName, "r2rml-mapping.ttl"))
            );
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

    private Path rawDirectory(String exampleName) {
        return OUTPUT_ROOT.resolve(exampleName).resolve("raw");
    }

    private Path generatedDirectory(String exampleName) {
        return OUTPUT_ROOT.resolve(exampleName).resolve("generated");
    }

    private int countRows(SqlInputParser.SqlData data) {
        return data.rowsByTable().values().stream()
            .mapToInt(java.util.List::size)
            .sum();
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
