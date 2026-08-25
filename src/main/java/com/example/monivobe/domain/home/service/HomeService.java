package com.example.monivobe.domain.home.service;

import com.example.monivobe.domain.home.converter.HomeConverter;
import com.example.monivobe.domain.home.dto.HomeResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.domain.transaction.entity.Budget;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.repository.BudgetRepository;
import com.example.monivobe.domain.transaction.repository.CategoryKeywordRepository;
import com.example.monivobe.domain.transaction.repository.CategoryRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
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

            budgetUsageRate =
                    Math.min(
                            budgetUsageRate,
                            100.0
                    );
        }



        // =====================================================
        // 지난달 같은 기간 지출 조회
        // =====================================================

        YearMonth previousMonth =
                yearMonth.minusMonths(1);

        /*
         * 예:
         * 현재가 8월 25일이면
         *
         * 이번 달 → 8월 1일 ~ 8월 25일
         * 지난달 → 7월 1일 ~ 7월 25일
         *
         * 이렇게 비교
         */

        LocalDate today = LocalDate.now();

        int currentDay =
                Math.min(
                        today.getDayOfMonth(),
                        yearMonth.lengthOfMonth()
                );

        /*
         * 선택한 달이 현재 달이 아니라면
         * 해당 월 전체를 비교 대상으로 사용
         */
        if (!yearMonth.equals(YearMonth.from(today))) {
            currentDay = yearMonth.lengthOfMonth();
        }

        LocalDateTime previousStartDate =
                previousMonth
                        .atDay(1)
                        .atStartOfDay();

        int previousLastDay =
                Math.min(
                        currentDay,
                        previousMonth.lengthOfMonth()
                );

        LocalDateTime previousEndDate =
                previousMonth
                        .atDay(previousLastDay)
                        .plusDays(1)
                        .atStartOfDay();


        Integer previousExpense =
                transactionRepository.getTotalExpense(
                        member,
                        previousStartDate,
                        previousEndDate
                );

        if (previousExpense == null) {
            previousExpense = 0;
        }


        // =====================================================
        // 지난달 대비 지출 차이
        // =====================================================

        int changeFromLastMonth =
                totalExpense - previousExpense;


        // =====================================================
        // 지난달 대비 지출 변화율
        // =====================================================

        double changeRateFromLastMonth;

        if (previousExpense == 0) {

            if (totalExpense == 0) {
                changeRateFromLastMonth = 0.0;
            } else {
                /*
                 * 지난달 지출이 0원인데
                 * 이번 달 지출이 발생한 경우
                 *
                 * 무한대 대신 100%로 표현하지 않고
                 * 별도 처리할 수 있도록 0으로 둠
                 */
                changeRateFromLastMonth = 0.0;
            }

        } else {

            changeRateFromLastMonth =
                    ((double) changeFromLastMonth
                            / previousExpense)
                            * 100;

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
                // 추가
                .changeFromLastMonth(changeFromLastMonth)
                .changeRateFromLastMonth(changeRateFromLastMonth)

                .build();
    }






    /**
     * 최근 거래
     */
    public HomeResDTO.RecentTransactions getRecentTransactions(
            Member member
    ) {

        List<Transaction> transactions =
                transactionRepository
                        .findTop3ByMemberOrderByDateDesc(
                                member
                        );

        List<HomeResDTO.RecentTransaction> result =
                transactions.stream()
                        .map(
                                HomeConverter::toRecentTransaction
                        )
                        .toList();

        return HomeResDTO.RecentTransactions.builder()
                .transactions(result)
                .build();
    }





    /**
     * 최근 6개월 지출
     *
     * 현재 달은 제외하고
     * 이전 6개월의 월별 총 지출을 반환
     */
    public List<HomeResDTO.MonthlySpending> getMonthlySpending(
            Member member
    ) {

        YearMonth currentMonth =
                YearMonth.now();

        List<HomeResDTO.MonthlySpending> result =
                new ArrayList<>();

        /*
         * 현재 달을 제외하고
         * 이전 6개월
         */
        for (int i = 6; i >= 1; i--) {

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
                    transactionRepository.getTotalExpense(
                            member,
                            startDate,
                            endDate
                    );

            result.add(
                    HomeResDTO.MonthlySpending.builder()
                            .month(
                                    targetMonth.getMonthValue()
                                            + "월"
                            )
                            .amount(
                                    amount == null
                                            ? 0
                                            : amount
                            )
                            .build()
            );
        }

        return result;
    }
}