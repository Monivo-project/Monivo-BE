package com.example.monivobe.domain.abnormal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AbnormalResDTO {

    @Builder
    public record AbnormalSpendingResDTO (
        Long transactionId,
        String merchant,
        Integer amount,
        String category,
        LocalDateTime date,
        Integer score,
        String type,
        String reason
    ){}

    @Builder
    public record AnalysisResult(
            int score,
            String reason
    ) {
    }
}
