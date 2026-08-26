package com.example.monivobe.domain.ontology.config;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OntologyConfig {

    private static final String ONTOLOGY_PATH =
            "./data/monivo-ontology";

    @Bean
    public Dataset ontologyDataset() {

        return TDB2Factory.connectDataset(
                ONTOLOGY_PATH
        );
    }
}