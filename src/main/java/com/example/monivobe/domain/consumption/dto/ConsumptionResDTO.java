package com.example.monivobe.domain.consumption.dto;

import com.example.monivobe.domain.transaction.enums.ClassificationType;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;


public class ConsumptionResDTO {

    @Builder
    public record GetConsumption(
            Integer year,
            Integer month,
            Integer totalAmount,
            Integer transactionCount,
            Integer abnormalCount,
            Integer uncategorizedCount,
            List<TransactionInfo> transactions
    ){}

    @Builder
    public record GetConsumptionCategory(
            Integer year,
            Integer month,
            List<CategoryAmount> categories
    ){}

    @Builder
    public record CategoryAmount(
            Long categoryId,
            String categoryName,
            Integer amount,
            Double percentage
    ){}

    @Builder
    public record TransactionInfo(
            Long transactionId,
            String merchant,
            Integer amount,
            LocalDateTime date,
            Long categoryId,
            String categoryName,
            ClassificationType classificationType,
            Boolean isAbnormal
    ){}

    @Builder
    public record GetConsumptionDetail(
            Long transactionId,
            String merchant,
            Integer amount,
            LocalDateTime date,
            Long categoryId,
            String categoryName,
            ClassificationType classificationType,
            Boolean isAbnormal,
            Boolean confidence
    ){}

    @Builder
    public record GetCategory(
            List<CategoryInfo> categories
    ){}

    @Builder
    public record CategoryInfo(
            Long categoryId,
            String name,
            String description
    ){}
}
