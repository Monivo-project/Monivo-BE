package com.example.monivobe.domain.home.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class HomeAiReqDTO {

    @Builder
    public record SpendingAnalysis (
        List<MonthlySpending> monthlySpending,
        List<CategorySpending> categorySpending,
        Integer currentMonthAmount,
        Integer currentMonthDays,
        Integer totalDaysInMonth
    ){}

    @Builder
    public record MonthlySpending (
            String month,
            Integer totalAmount
    ){}

    @Builder
    public record CategorySpending  (
            String category,
            Integer amount
    ){}
}
