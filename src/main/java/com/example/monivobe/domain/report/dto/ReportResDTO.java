package com.example.monivobe.domain.report.dto;

import lombok.Builder;
import java.util.List;

public class ReportResDTO {
    /**
     * 전체 소비 분석 리포트
     */
    @Builder
    public record Report(
            Integer year,
            Integer month,
            List<MonthlyExpense> monthlyExpenses,
            List<CategoryExpense> categoryExpenses,
            List<CategoryBudgetComparison> budgetComparisons
    ) {
    }


    /**
     * 최근 6개월 지출
     */
    @Builder
    public record MonthlyExpense(
            Integer year,
            Integer month,
            String label,
            Integer amount
    ) {
    }


    /**
     * 카테고리별 지출
     */
    @Builder
    public record CategoryExpense(
            Long categoryId,
            String categoryName,
            Integer amount,
            Double percentage
    ) {
    }


    /**
     * 카테고리별 예산 대비 실제 지출
     */
    @Builder
    public record CategoryBudgetComparison(
            Long categoryId,
            String categoryName,
            Integer budget,
            Integer actualAmount,
            Double usageRate,
            Boolean overBudget
    ) {
    }
}
