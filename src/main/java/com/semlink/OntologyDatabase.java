package com.semlink;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class OntologyDatabase {
    private static final Logger log = LoggerFactory.getLogger(OntologyDatabase.class);
    private final Dataset dataset;

    public OntologyDatabase() {
        // Initialize TDB2 dataset in the target directory
        this.dataset = TDB2Factory.connectDataset("target/tdb-database");
        log.info("Initialized Jena TDB2 Database for Ontology persistence.");
    }

    public void addOntology(String name, Model model) {
        dataset.executeWrite(() -> {
            Model graph = dataset.getNamedModel(name);
            graph.removeAll(); // Clear existing if any
            graph.add(model);
            log.info("Saved ontology to DB: {}", name);
        });
    }

    public void removeOntology(String name) {
        dataset.executeWrite(() -> {
            if (dataset.containsNamedModel(name)) {
                dataset.removeNamedModel(name);
                log.info("Removed ontology from DB: {}", name);
            }
        });
    }

    public Model getOntology(String name) {
        return dataset.calculateRead(() -> {
            if (dataset.containsNamedModel(name)) {
                Model copy = ModelFactory.createDefaultModel();
                copy.add(dataset.getNamedModel(name));
                return copy;
            }
            return null;
        });
    }

    public List<String> listOntologies() {
        return dataset.calculateRead(() -> {
            List<String> names = new ArrayList<>();
            dataset.listNames().forEachRemaining(names::add);
            return names;
        });
    }

    public Model getAllOntologiesMerged() {
        return dataset.calculateRead(() -> {
            Model merged = ModelFactory.createDefaultModel();
            dataset.listNames().forEachRemaining(name -> merged.add(dataset.getNamedModel(name)));
            // Also include default model if anything is there
            merged.add(dataset.getDefaultModel());
            return merged;
        });
    }
    
    public boolean isEmpty() {
        return dataset.calculateRead(() -> !dataset.listNames().hasNext());
    }

    @PreDestroy
    public void close() {
        if (dataset != null) {
            dataset.close();
            log.info("Closed Jena TDB2 Database.");
        }
    }
}
