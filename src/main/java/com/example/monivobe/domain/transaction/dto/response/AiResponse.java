package com.example.monivobe.domain.transaction.dto.response;

public class AiResponse {
    public record TransactionClassification(
            Long transactionId,
            Long categoryId,
            Double confidence,
            String reason
    ) {
    }
}
