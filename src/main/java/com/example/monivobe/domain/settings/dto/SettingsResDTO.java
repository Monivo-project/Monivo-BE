package com.example.monivobe.domain.settings.dto;

import lombok.Builder;
import java.time.LocalDate;
import java.util.List;

public class SettingsResDTO {

    /**
     * 정기결제 후보 목록
     */
    @Builder
    public record GetCandidates(
            List<Candidates> candidates
    ) {}

    /**
     * 정기결제 후보
     */
    @Builder
    public record Candidates(
            Long candidateId,
            String merchant,
            Integer transactionCount,
            Integer averageAmount,
            String billingCycle,
            LocalDate nextPaymentDate
    ) {}

    /**
     * 정기결제 후보 상세
     */
    @Builder
    public record GetCandidatesDetail(
            Long candidateId,
            String merchant,
            Integer transactionCount,
            Integer averageAmount,
            String billingCycle,
            LocalDate nextPaymentDate,
            List<TransactionDetail> transactions
    ) {}

    /**
     * 후보의 실제 결제내역
     */
    @Builder
    public record TransactionDetail(
            Long transactionId,
            LocalDate date,
            Integer amount
    ) {}

}