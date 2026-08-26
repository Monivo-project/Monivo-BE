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

            Integer uncategorizedCount,
            // 지난달 같은 기간 대비 지출 차이
            Integer changeFromLastMonth,

            // 지난달 같은 기간 대비 지출 변화율
            Double changeRateFromLastMonth
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

    @Builder
    public record ExpectedBudget (
            // AI가 예측한 이번 달 예상 지출
            Integer expectedAmount,
            // 사용자가 예상 지출에 맞춰 사용할 것을 권장하는 예산
            Integer recommendedBudget,
            // 현재까지 이번 달 사용한 금액
            Integer currentAmount,
            // 이번 달 남은 예상 지출
            Integer remainingExpectedAmount,
            // 예측 근거
            String reason,
            // 신뢰도
            Integer confidence,
            // 기준이 된 개월 수
            Integer analyzedMonths
    ){}

    @Builder
    public record MonthlySpending (
            String month,
            Integer amount

    ){}
}
