package com.example.monivobe.domain.transaction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TransactionResDTO {

    @Getter
    @AllArgsConstructor
    public static class UploadResponse {
        private String status;
        private String message;
    }

    public record TransactionOntologyContext(
            Long transactionId,
            String merchantName,
            String categoryName,
            String parentCategoryName
    ) {
    }
}
