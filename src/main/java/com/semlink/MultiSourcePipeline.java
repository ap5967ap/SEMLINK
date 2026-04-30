package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
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

/**
 * Framework pipeline for connection-driven ingestion. Each source is discovered,
 * exported to RDF, validated, and written into an isolated output directory
 * before all source graphs are merged.
 */
public class MultiSourcePipeline {
    private final AdapterRegistry adapterRegistry;
    private final Path outputDirectory;

    public MultiSourcePipeline(AdapterRegistry adapterRegistry, Path outputDirectory) {
        this.adapterRegistry = adapterRegistry;
        this.outputDirectory = outputDirectory;
    }

    public PipelineResult run(List<ConnectionConfig> connections, MappingRules mappingRules) {
        List<SourcePipelineResult> sourceResults = new ArrayList<>();
        Model merged = ModelFactory.createDefaultModel();
        for (ConnectionConfig connection : connections) {
            DatabaseAdapter adapter = adapterRegistry.create(connection);
            SchemaDescriptor schema = adapter.extractSchema();
            Model raw = adapter.exportToRDF(mappingRules);
            ValidationReport validationReport = adapter.validate(raw);
            merged.add(raw);

            Path sourceDir = outputDirectory.resolve(connection.id());
            writeSourceArtifacts(sourceDir, schema, raw, validationReport);
            sourceResults.add(new SourcePipelineResult(
                connection.id(),
                adapter.getDataModelType(),
                schema.entities().size(),
                schema.attributes().size(),
                raw.size(),
                validationReport.conforms(),
                sourceDir
            ));
        }

        Path mergedPath = outputDirectory.resolve("merged.ttl");
        writeModel(merged, mergedPath);
        writeString(outputDirectory.resolve("pipeline-summary.txt"), summary(sourceResults, merged.size()));
        return new PipelineResult(sourceResults, merged.size(), mergedPath, outputDirectory);
    }

    private void writeSourceArtifacts(Path sourceDir, SchemaDescriptor schema, Model raw, ValidationReport report) {
        writeString(sourceDir.resolve("schema.txt"), schemaText(schema));
        writeModel(raw, sourceDir.resolve("raw-export.ttl"));
        writeModel(report.getModel(), sourceDir.resolve("validation-report.ttl"));
        writeString(sourceDir.resolve("mapping-review.txt"), mappingReview(schema));
    }

    private String schemaText(SchemaDescriptor schema) {
        return "sourceId=" + schema.sourceId() + "\n"
            + "dataModelType=" + schema.dataModelType() + "\n"
            + "entities=" + schema.entities() + "\n"
            + "attributes=" + schema.attributes() + "\n"
            + "relationships=" + schema.relationships() + "\n";
    }

    private String mappingReview(SchemaDescriptor schema) {
        return "SEMLINK assisted mapping review for " + schema.sourceId() + "\n"
            + "Entities discovered: " + schema.entities().size() + "\n"
            + "Attributes discovered: " + schema.attributes().size() + "\n"
            + "Relationships discovered: " + schema.relationships().size() + "\n"
            + "Next step: approve high-confidence mappings into the central ontology.\n";
    }

    private String summary(List<SourcePipelineResult> sources, long mergedTriples) {
        StringBuilder builder = new StringBuilder("SEMLINK multi-source pipeline completed.\n");
        builder.append("Sources: ").append(sources.size()).append('\n');
        builder.append("Merged triples: ").append(mergedTriples).append('\n');
        for (SourcePipelineResult source : sources) {
            builder.append("- ").append(source.sourceId()).append(" [")
                .append(source.dataModelType()).append("] triples=")
                .append(source.tripleCount()).append(" conforms=")
                .append(source.validationConforms()).append('\n');
        }
        return builder.toString();
    }

    private void writeModel(Model model, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                RDFDataMgr.write(outputStream, model, Lang.TURTLE);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write RDF model to " + outputPath, exception);
        }
    }

    private void writeString(Path outputPath, String content) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write " + outputPath, exception);
        }
    }
}
