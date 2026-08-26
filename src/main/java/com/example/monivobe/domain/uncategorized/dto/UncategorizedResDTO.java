package com.example.monivobe.domain.uncategorized.dto;

import com.example.monivobe.domain.home.dto.HomeAiReqDTO;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

public class UncategorizedResDTO {

    @Builder
    public record GetUncategorized(
            Long transactionId,
            String merchant,
            Long candidateCategoryId,
            String candidateCategoryName,
            LocalDate date,
            Integer amount,
            Double confidence
    ) {
    }
}
