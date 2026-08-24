package com.example.monivobe.domain.home.dto;

import com.example.monivobe.domain.transaction.enums.ClassificationType;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class HomeResDTO {

    @Builder
    public record Summary(
            Integer year,
            Integer month,

            Integer totalExpense,

            Integer budget,

            Integer remainingBudget,

            Double budgetUsageRate,

            Integer abnormalCount,

            Integer uncategorizedCount
    ) {
    }


    /**
     * 이번 주 일별 지출
     */
    @Builder
    public record DailyExpense(
            LocalDate date,
            String dayOfWeek,
            Integer amount
    ) {
    }


    /**
     * 최근 거래
     */
    @Builder
    public record RecentTransaction(
            Long transactionId,
            String merchant,
            Integer amount,
            LocalDateTime date,
            Long categoryId,
            String categoryName,
            ClassificationType classificationType,
            Boolean isAbnormal
    ) {
    }


    /**
     * 이번 주 지출 전체 응답
     */
    @Builder
    public record WeeklyExpense(
            LocalDate startDate,
            LocalDate endDate,
            List<DailyExpense> dailyExpenses
    ) {
    }


    /**
     * 최근 거래 전체 응답
     */
    @Builder
    public record RecentTransactions(
            List<RecentTransaction> transactions
    ) {
    }
}
