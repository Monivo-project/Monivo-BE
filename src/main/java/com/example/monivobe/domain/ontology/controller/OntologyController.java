package com.example.monivobe.domain.ontology.controller;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class OntologyController {

    private final Dataset ontologyDataset;

    @GetMapping("/ontology/transactions")
    public List<String> getTransactions() {

        String sparql = """
                SELECT ?transaction ?amount ?date ?classification
                WHERE {
                    ?transaction
                        a <http://monivo.com/ontology#Transaction> .

                    OPTIONAL {
                        ?transaction
                            <http://monivo.com/ontology#hasAmount>
                            ?amount .
                    }

                    OPTIONAL {
                        ?transaction
                            <http://monivo.com/ontology#transactionDate>
                            ?date .
                    }

                    OPTIONAL {
                        ?transaction
                            <http://monivo.com/ontology#classificationType>
                            ?classification .
                    }
                }
                ORDER BY ?transaction
                """;

        Query query =
                QueryFactory.create(sparql);

        List<String> result =
                new ArrayList<>();

        ontologyDataset.begin(
                ReadWrite.READ
        );

        try (
                QueryExecution execution =
                        QueryExecution
                                .dataset(ontologyDataset)
                                .query(query)
                                .build()
        ) {

            var resultSet =
                    execution.execSelect();

            while (resultSet.hasNext()) {

                QuerySolution solution =
                        resultSet.next();

                result.add(
                        solution.toString()
                );
            }

        } finally {

            ontologyDataset.end();
        }

        return result;
    }
}