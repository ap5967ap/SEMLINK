package com.semlink;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        OntModel model = OntModelFactory.createModel();

        String ns = "https://example.org/basic#";

        OntClass collegeClass = model.createOntClass(ns + "College");
        OntIndividual myCollege = collegeClass.createIndividual(ns + "MyFirstCollege");

        System.out.println("--- Generated Ontology (Turtle Format) ---");
        RDFDataMgr.write(System.out, model, Lang.TURTLE);

        try (FileOutputStream out = new FileOutputStream("basic_ontology.owl")) {
            RDFDataMgr.write(out, model, Lang.RDFXML);
            System.out.println("\nSUCCESS: 'basic_ontology.owl' created in project root.");
            logger.info("Created individual: {}", myCollege.getURI());
        } catch (Exception e) {
            logger.error("An error occurred: {}", e.getMessage());
        }
    }
}