package com.semlink;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ValidationReport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Public Java facade for embedding SEMLINK as a framework.
 */
public class SemLink {
    private final String centralOntology;
    private final List<ConnectionConfig> connections;
    private final MappingRules mappingRules;
    private final ReasonerType reasonerType;
    private final ShaclShapes shaclShapes;
    private final Path outputDir;
    private Model latestMergedModel;

    private SemLink(Builder builder) {
        this.centralOntology = builder.centralOntology;
        this.connections = List.copyOf(builder.connections);
        this.mappingRules = builder.mappingRules;
        this.reasonerType = builder.reasonerType;
        this.shaclShapes = builder.shaclShapes;
        this.outputDir = builder.outputDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PipelineResult runPipeline() {
        MultiSourcePipeline pipeline = new MultiSourcePipeline(AdapterRegistry.withDefaults(), outputDir);
        PipelineResult result = pipeline.run(connections, mappingRules);
        latestMergedModel = RDFDataMgr.loadModel(result.mergedModelPath().toString());
        return result;
    }

    public ResultSet query(String sparql) {
        Model model = latestMergedModel;
        if (model == null) {
            runPipeline();
            model = latestMergedModel;
        }
        Query query = QueryFactory.create(sparql);
        try (QueryExecution execution = QueryExecutionFactory.create(query, model)) {
            return ResultSetFactory.copyResults(execution.execSelect());
        }
    }

    public ResultSet queryNL(String question) {
        return query(NLQueryTranslator.withoutRemoteModel().translate(question));
    }

    public ValidationReport validate(String sourceId) {
        ConnectionConfig match = connections.stream()
            .filter(connection -> connection.id().equals(sourceId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + sourceId));
        DatabaseAdapter adapter = AdapterRegistry.withDefaults().create(match);
        return adapter.validate(adapter.exportToRDF(mappingRules));
    }

    public String centralOntology() {
        return centralOntology;
    }

    public ReasonerType reasonerType() {
        return reasonerType;
    }

    public ShaclShapes shaclShapes() {
        return shaclShapes;
    }

    public static class Builder {
        private String centralOntology = "classpath:semantic/ontologies/central/aicte.ttl";
        private final List<ConnectionConfig> connections = new ArrayList<>();
        private MappingRules mappingRules = MappingRules.assisted();
        private ReasonerType reasonerType = ReasonerType.OWL_MICRO;
        private ShaclShapes shaclShapes = ShaclShapes.from("src/main/resources/semantic/shapes/aicte-shapes.ttl");
        private Path outputDir = Path.of("target", "semantic-output", "sdk");

        public Builder centralOntology(String centralOntology) {
            this.centralOntology = centralOntology;
            return this;
        }

        public Builder connect(ConnectionConfig connectionConfig) {
            this.connections.add(connectionConfig);
            return this;
        }

        public Builder withMappingRules(MappingRules mappingRules) {
            this.mappingRules = mappingRules;
            return this;
        }

        public Builder withReasoner(ReasonerType reasonerType) {
            this.reasonerType = reasonerType;
            return this;
        }

        public Builder withValidation(ShaclShapes shaclShapes) {
            this.shaclShapes = shaclShapes;
            return this;
        }

        public Builder outputDir(String outputDir) {
            this.outputDir = Path.of(outputDir);
            return this;
        }

        public SemLink build() {
            if (connections.isEmpty()) {
                throw new IllegalStateException("At least one connection is required.");
            }
            return new SemLink(this);
        }
    }
}
