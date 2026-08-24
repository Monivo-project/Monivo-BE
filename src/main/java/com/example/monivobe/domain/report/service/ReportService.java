package com.example.monivobe.domain.report.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.report.converter.ReportConverter;
import com.example.monivobe.domain.report.dto.ReportResDTO;
import com.example.monivobe.domain.transaction.entity.CategoryBudget;
import com.example.monivobe.domain.transaction.repository.CategoryBudgetRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;


    /**
     * 전체 소비 분석 리포트
     */
    public ReportResDTO.Report getReport(
            Integer year,
            Integer month,
            Member member
    ) {

        List<ReportResDTO.MonthlyExpense> monthlyExpenses =
                getMonthlyExpenses(
                        year,
                        month,
                        member
                );


        List<ReportResDTO.CategoryExpense> categoryExpenses =
                getCategoryExpenses(
                        year,
                        month,
                        member
                );


        List<ReportResDTO.CategoryBudgetComparison>
                budgetComparisons =
                getBudgetComparisons(
                        year,
                        month,
                        member
                );


        return ReportResDTO.Report.builder()
                .year(year)
                .month(month)
                .monthlyExpenses(monthlyExpenses)
                .categoryExpenses(categoryExpenses)
                .budgetComparisons(budgetComparisons)
                .build();
    }


    /**
     * 최근 6개월 지출
     */
    private List<ReportResDTO.MonthlyExpense>
    getMonthlyExpenses(
            Integer year,
            Integer month,
            Member member
    ) {

        List<ReportResDTO.MonthlyExpense> result =
                new ArrayList<>();


        YearMonth currentMonth =
                YearMonth.of(year, month);


        for (int i = 5; i >= 0; i--) {

            YearMonth targetMonth =
                    currentMonth.minusMonths(i);


            LocalDateTime startDate =
                    targetMonth
                            .atDay(1)
                            .atStartOfDay();


            LocalDateTime endDate =
                    targetMonth
                            .plusMonths(1)
                            .atDay(1)
                            .atStartOfDay();


            Integer amount =
                    transactionRepository.getTotalAmount(
                            member,
                            startDate,
                            endDate
                    );


            result.add(
                    ReportResDTO.MonthlyExpense.builder()
                            .year(targetMonth.getYear())
                            .month(targetMonth.getMonthValue())
                            .label(
                                    targetMonth.getMonthValue()
                                            + "월"
                            )
                            .amount(amount)
                            .build()
            );
        }

        return result;
    }


    /**
     * 현재 월 카테고리별 지출
     */
    private List<ReportResDTO.CategoryExpense>
    getCategoryExpenses(
            Integer year,
            Integer month,
            Member member
    ) {

        YearMonth yearMonth =
                YearMonth.of(year, month);


        LocalDateTime startDate =
                yearMonth
                        .atDay(1)
                        .atStartOfDay();


        LocalDateTime endDate =
                yearMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay();


        Integer totalAmount =
                transactionRepository.getTotalAmount(
                        member,
                        startDate,
                        endDate
                );


        List<Object[]> rows =
                transactionRepository.getCategoryExpenses(
                        member,
                        startDate,
                        endDate
                );


        List<ReportResDTO.CategoryExpense> result =
                new ArrayList<>();


        for (Object[] row : rows) {

            Long categoryId =
                    (Long) row[0];

            String categoryName =
                    row[1] != null
                            ? (String) row[1]
                            : "미분류";

            Integer amount =
                    ((Number) row[2]).intValue();


            double percentage = 0.0;

            if (totalAmount != null && totalAmount > 0) {
                percentage =
                        ((double) amount / totalAmount) * 100;
            }


            result.add(
                    ReportResDTO.CategoryExpense.builder()
                            .categoryId(categoryId)
                            .categoryName(categoryName)
                            .amount(amount)
                            .percentage(
                                    Math.round(
                                            percentage * 10
                                    ) / 10.0
                            )
                            .build()
            );
        }

        return result;
    }


    /**
     * 카테고리별 예산 대비 실제 지출
     */
    private List<ReportResDTO.CategoryBudgetComparison>
    getBudgetComparisons(
            Integer year,
            Integer month,
            Member member
    ) {

        List<CategoryBudget> budgets =
                categoryBudgetRepository
                        .findByMemberAndYearAndMonth(
                                member,
                                year,
                                month
                        );


        YearMonth yearMonth =
                YearMonth.of(year, month);


        LocalDateTime startDate =
                yearMonth
                        .atDay(1)
                        .atStartOfDay();


        LocalDateTime endDate =
                yearMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay();


        List<Object[]> rows =
                transactionRepository.getCategoryExpenses(
                        member,
                        startDate,
                        endDate
                );


        Map<Long, Integer> actualMap =
                new HashMap<>();


        for (Object[] row : rows) {

            Long categoryId =
                    (Long) row[0];

            Integer amount =
                    ((Number) row[2]).intValue();


            if (categoryId != null) {
                actualMap.put(
                        categoryId,
                        amount
                );
            }
        }


        List<ReportResDTO.CategoryBudgetComparison>
                result =
                new ArrayList<>();


        for (CategoryBudget budget : budgets) {

            Long categoryId =
                    budget.getCategory().getId();


            Integer actualAmount =
                    actualMap.getOrDefault(
                            categoryId,
                            0
                    );


            result.add(
                    ReportConverter
                            .toCategoryBudgetComparison(
                                    budget,
                                    actualAmount
                            )
            );
        }


        return result;
    }
}