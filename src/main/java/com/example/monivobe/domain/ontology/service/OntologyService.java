package com.example.monivobe.domain.ontology.service;

import com.example.monivobe.domain.ontology.dto.OntologyResDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
public class OntologyService {

    private static final String NS =
            "http://monivo.com/ontology#";

    private Model model;

    @PostConstruct
    public void init() {
        try {
            model = ModelFactory.createDefaultModel();

            ClassPathResource resource =
                    new ClassPathResource("ontology/monivo.ttl");

            try (InputStream inputStream =
                         resource.getInputStream()) {

                model.read(
                        inputStream,
                        null,
                        "TURTLE"
                );
            }

            log.info(
                    "Monivo Ontology loaded. triples={}",
                    model.size()
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Monivo ontology loading failed",
                    e
            );
        }
    }

    public Optional<OntologyResDTO.OntologyCategoryResult> classifyMerchant(
            String merchant
    ) {

        if (merchant == null || merchant.isBlank()) {
            return Optional.empty();
        }

        String normalized =
                merchant.trim();

        String queryString = """
                PREFIX monivo: <http://monivo.com/ontology#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                SELECT ?merchant ?businessType ?businessLabel
                       ?category ?categoryLabel

                WHERE {
                    ?merchant a monivo:Merchant ;
                              rdfs:label ?merchantLabel ;
                              monivo:hasBusinessType ?businessType .

                    ?businessType rdfs:label ?businessLabel ;
                                  monivo:belongsTo ?category .

                    ?category rdfs:label ?categoryLabel .

                    FILTER(
                        STR(?merchantLabel) = "%s"
                    )
                }
                """.formatted(
                escapeSparql(normalized)
        );

        Query query =
                QueryFactory.create(queryString);

        try (
                QueryExecution execution =
                        QueryExecution.create()
                                .query(query)
                                .model(model)
                                .build()
        ) {

            ResultSet resultSet =
                    execution.execSelect();

            if (!resultSet.hasNext()) {
                return Optional.empty();
            }

            QuerySolution solution =
                    resultSet.next();

            String businessType =
                    solution
                            .getLiteral("businessLabel")
                            .getString();

            String category =
                    solution
                            .getLiteral("categoryLabel")
                            .getString();

            return Optional.of(
                    new OntologyResDTO.OntologyCategoryResult(
                            normalized,
                            businessType,
                            category
                    )
            );
        }
    }

    private String escapeSparql(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
