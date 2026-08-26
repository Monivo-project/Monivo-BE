package com.example.monivobe.domain.ontology.dto;

import com.example.monivobe.domain.transaction.entity.Category;

public class OntologyResDTO {
    public record OntologyCategoryResult(
            String merchant,
            String subCategory,
            String category
    ) {
    }

    public record ClassificationResult(
            Category category,
            String subCategory,
            String source
    ) {
        public static ClassificationResult unclassified() {
            return new ClassificationResult(
                    null,
                    null,
                    "UNCLASSIFIED"
            );
        }
    }
}
