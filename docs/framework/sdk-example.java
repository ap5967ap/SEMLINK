package docs.framework;

import com.semlink.ConnectionConfig;
import com.semlink.MappingRules;
import com.semlink.PipelineResult;
import com.semlink.ReasonerType;
import com.semlink.SemLink;
import com.semlink.ShaclShapes;
import org.apache.jena.query.ResultSet;
import org.apache.jena.shacl.ValidationReport;

public class SdkExample {
    public static void main(String[] args) {
        SemLink semlink = SemLink.builder()
            .centralOntology("classpath:semantic/ontologies/central/aicte.ttl")
            .connect(ConnectionConfig.mysql("university1", "localhost", 3306, "university1", "root", "pass"))
            .connect(ConnectionConfig.mongo("university2", "mongodb://localhost:27017/university2"))
            .connect(ConnectionConfig.neo4j("university3", "bolt://localhost:7687", "neo4j", "pass"))
            .connect(ConnectionConfig.owlFile("university4", "src/main/resources/semantic/ontologies/local/university4/university4.ttl"))
            .withMappingRules(MappingRules.assisted())
            .withReasoner(ReasonerType.OWL_MICRO)
            .withValidation(ShaclShapes.from("src/main/resources/semantic/shapes/aicte-shapes.ttl"))
            .outputDir("target/semantic-output/sdk")
            .build();

        PipelineResult result = semlink.runPipeline();
        System.out.println("Merged triples: " + result.getMergedModelSize());
        System.out.println("Quality score: " + result.getQualityScore());

        ResultSet students = semlink.query("SELECT ?s WHERE { ?s a <https://semlink.example.org/aicte#Student> } LIMIT 10");
        System.out.println("Has students: " + students.hasNext());

        ResultSet natural = semlink.queryNL("Show students with CGPA above 9 from all universities");
        System.out.println("Natural query has rows: " + natural.hasNext());

        ValidationReport report = semlink.validate("university4");
        System.out.println("University4 conforms: " + report.conforms());
    }
}
