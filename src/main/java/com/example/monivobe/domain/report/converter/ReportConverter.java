package com.example.monivobe.domain.report.converter;

import com.example.monivobe.domain.report.dto.ReportResDTO;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.CategoryBudget;

public class ReportConverter {

    private ReportConverter() {
    }

    public static ReportResDTO.CategoryBudgetComparison
    toCategoryBudgetComparison(
            CategoryBudget budget,
            Integer actualAmount
    ) {

        Category category = budget.getCategory();

        int budgetAmount = budget.getAmount();

        double usageRate;

        if (budgetAmount == 0) {
            usageRate = actualAmount > 0 ? 100.0 : 0.0;
        } else {
            usageRate =
                    ((double) actualAmount / budgetAmount) * 100;
        }

        return ReportResDTO.CategoryBudgetComparison.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .budget(budgetAmount)
                .actualAmount(actualAmount)
                .usageRate(usageRate)
                .overBudget(actualAmount > budgetAmount)
                .build();
    }
}