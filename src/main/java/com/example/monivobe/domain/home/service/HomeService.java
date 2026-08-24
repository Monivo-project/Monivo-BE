package com.example.monivobe.domain.home.service;

import com.example.monivobe.domain.home.converter.HomeConverter;
import com.example.monivobe.domain.home.dto.HomeResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Budget;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.repository.BudgetRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;


    /**
     * 대시보드 상단 요약
     */
    public HomeResDTO.Summary getSummary(
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


        // 총 지출
        Integer totalExpense =
                transactionRepository.getTotalExpense(
                        member,
                        startDate,
                        endDate
                );


        // 이상 지출
        Integer abnormalCount =
                transactionRepository.countAbnormalTransactions(
                        member,
                        startDate,
                        endDate
                );


        // 미분류
        Integer uncategorizedCount =
                transactionRepository.countUnclassifiedTransactions(
                        member,
                        startDate,
                        endDate
                );


        // 예산
        Integer budgetAmount =
                budgetRepository
                        .findByMemberAndYearAndMonth(
                                member,
                                year,
                                month
                        )
                        .map(Budget::getAmount)
                        .orElse(0);


        // 잔여 예산
        int remainingBudget =
                Math.max(
                        budgetAmount - totalExpense,
                        0
                );


        // 예산 사용률
        double budgetUsageRate;

        if (budgetAmount == 0) {
            budgetUsageRate = 0.0;
        } else {
            budgetUsageRate =
                    ((double) totalExpense / budgetAmount) * 100;

            // 화면에서 100% 이상을 표시하지 않으려면
            budgetUsageRate =
                    Math.min(budgetUsageRate, 100.0);
        }


        return HomeResDTO.Summary.builder()
                .year(year)
                .month(month)
                .totalExpense(totalExpense)
                .budget(budgetAmount)
                .remainingBudget(remainingBudget)
                .budgetUsageRate(budgetUsageRate)
                .abnormalCount(abnormalCount)
                .uncategorizedCount(uncategorizedCount)
                .build();
    }


    /**
     * 이번 주 일별 지출
     */
    public HomeResDTO.WeeklyExpense getWeeklyExpense(
            LocalDate date,
            Member member
    ) {

        // 월요일
        LocalDate startDate =
                date.with(
                        TemporalAdjusters.previousOrSame(
                                DayOfWeek.MONDAY
                        )
                );

        // 일요일 다음 날
        LocalDate endDate =
                startDate.plusDays(7);


        List<HomeResDTO.DailyExpense> dailyExpenses =
                new ArrayList<>();


        for (int i = 0; i < 7; i++) {

            LocalDate currentDate =
                    startDate.plusDays(i);

            LocalDateTime dayStart =
                    currentDate.atStartOfDay();

            LocalDateTime dayEnd =
                    currentDate
                            .plusDays(1)
                            .atStartOfDay();


            Integer amount =
                    transactionRepository.getDailyExpense(
                            member,
                            dayStart,
                            dayEnd
                    );


            dailyExpenses.add(
                    HomeResDTO.DailyExpense.builder()
                            .date(currentDate)
                            .dayOfWeek(
                                    getKoreanDayOfWeek(
                                            currentDate.getDayOfWeek()
                                    )
                            )
                            .amount(amount)
                            .build()
            );
        }


        return HomeResDTO.WeeklyExpense.builder()
                .startDate(startDate)
                .endDate(endDate.minusDays(1))
                .dailyExpenses(dailyExpenses)
                .build();
    }


    /**
     * 최근 거래
     */
    public HomeResDTO.RecentTransactions
    getRecentTransactions(Member member) {

        List<Transaction> transactions =
                transactionRepository
                        .findTop5ByMemberOrderByDateDesc(member);


        List<HomeResDTO.RecentTransaction> result =
                transactions.stream()
                        .map(
                               HomeConverter
                                        ::toRecentTransaction
                        )
                        .toList();


        return HomeResDTO.RecentTransactions.builder()
                .transactions(result)
                .build();
    }


    /**
     * 요일 → 한글
     */
    private String getKoreanDayOfWeek(
            DayOfWeek dayOfWeek
    ) {

        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }
}
