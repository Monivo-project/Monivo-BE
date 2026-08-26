package com.example.monivobe.domain.home.service;

import com.example.monivobe.domain.home.converter.HomeConverter;
import com.example.monivobe.domain.home.dto.HomeAiReqDTO;
import com.example.monivobe.domain.home.dto.HomeAiResDTO;
import com.example.monivobe.domain.home.dto.HomeResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.ExpectedBudget;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.TransactionType;
import com.example.monivobe.domain.transaction.repository.ExpectedBudgetRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpectedBudgetService {

    private final TransactionRepository transactionRepository;
    private final BudgetPredictionService budgetPredictionService;
    private final ExpectedBudgetRepository expectedBudgetRepository;


    /**
     * 선택한 월의 예상 지출 조회
     *
     * 예:
     * /api/home/expected-budget?year=2026&month=8
     */
    @Transactional(readOnly = true)
    public HomeResDTO.ExpectedBudget getExpectedBudget(
            Member member,
            Integer year,
            Integer month
    ) {

        YearMonth targetMonth =
                YearMonth.of(year, month);

        /*
         * 해당 회원 + 해당 연도 + 해당 월의
         * 예상 지출 데이터가 이미 존재하는지 확인
         */
        Optional<ExpectedBudget> budget =
                expectedBudgetRepository
                        .findByMemberAndTargetYearAndTargetMonth(
                                member,
                                targetMonth.getYear(),
                                targetMonth.getMonthValue()
                        );

        /*
         * 이미 생성된 데이터가 있으면
         * AI를 다시 호출하지 않고 DB 데이터 반환
         */
        if (budget.isPresent()) {

            return HomeConverter.toResponse(
                    budget.get()
            );
        }

        /*
         * 데이터가 없다면 AI 분석 후 생성
         */
        return createExpectedBudget(
                member,
                targetMonth
        );
    }


    /**
     * 선택한 월의 예상 지출 생성
     *
     * DB INSERT가 발생하므로
     * 새로운 쓰기 트랜잭션에서 실행
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public HomeResDTO.ExpectedBudget createExpectedBudget(
            Member member,
            YearMonth targetMonth
    ) {

        /*
         * AI 분석에 사용할 기간
         *
         * 선택한 월 기준으로
         * 이전 6개월 + 선택한 월 데이터를 조회
         */
        YearMonth analysisStartMonth =
                targetMonth.minusMonths(6);

        LocalDateTime startDate =
                analysisStartMonth
                        .atDay(1)
                        .atStartOfDay();

        LocalDateTime endDate =
                targetMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay();


        /*
         * 거래내역 조회
         */
        List<Transaction> transactions =
                transactionRepository
                        .findByMemberAndDateBetweenOrderByDateAsc(
                                member,
                                startDate,
                                endDate
                        );


        /*
         * 월별 지출
         *
         * EXPENSE만 계산
         */
        Map<YearMonth, Integer> monthlyAmount =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getTransactionType()
                                        == TransactionType.EXPENSE
                        )
                        .collect(
                                Collectors.groupingBy(
                                        transaction ->
                                                YearMonth.from(
                                                        transaction.getDate()
                                                ),
                                        TreeMap::new,
                                        Collectors.summingInt(
                                                Transaction::getAmount
                                        )
                                )
                        );


        /*
         * 카테고리별 지출
         *
         * EXPENSE만 계산
         */
        Map<String, Integer> categoryAmount =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getTransactionType()
                                        == TransactionType.EXPENSE
                        )
                        .filter(transaction ->
                                transaction.getCategory() != null
                        )
                        .collect(
                                Collectors.groupingBy(
                                        transaction ->
                                                transaction
                                                        .getCategory()
                                                        .getName(),
                                        Collectors.summingInt(
                                                Transaction::getAmount
                                        )
                                )
                        );


        /*
         * 선택한 월의 현재 지출
         */
        int currentAmount =
                monthlyAmount.getOrDefault(
                        targetMonth,
                        0
                );


        /*
         * 현재 날짜가 선택한 월에 포함되는지 확인
         *
         * 현재 달이면
         * 오늘까지의 날짜를 사용
         *
         * 과거 달이면
         * 해당 월 전체 날짜를 사용
         */
        YearMonth currentMonth =
                YearMonth.now();

        int currentDays;

        if (targetMonth.equals(currentMonth)) {

            currentDays =
                    java.time.LocalDate.now()
                            .getDayOfMonth();

        } else {

            currentDays =
                    targetMonth.lengthOfMonth();
        }


        int totalDays =
                targetMonth.lengthOfMonth();


        /*
         * AI 분석 요청 데이터 생성
         */
        HomeAiReqDTO.SpendingAnalysis analysis =
                HomeAiReqDTO.SpendingAnalysis.builder()

                        /*
                         * 최근 6개월 + 선택한 월
                         */
                        .monthlySpending(
                                monthlyAmount.entrySet()
                                        .stream()
                                        .map(entry ->
                                                HomeAiReqDTO.MonthlySpending
                                                        .builder()
                                                        .month(
                                                                entry.getKey()
                                                                        .toString()
                                                        )
                                                        .totalAmount(
                                                                entry.getValue()
                                                        )
                                                        .build()
                                        )
                                        .toList()
                        )

                        /*
                         * 카테고리별 지출
                         */
                        .categorySpending(
                                categoryAmount.entrySet()
                                        .stream()
                                        .map(entry ->
                                                HomeAiReqDTO.CategorySpending
                                                        .builder()
                                                        .category(
                                                                entry.getKey()
                                                        )
                                                        .amount(
                                                                entry.getValue()
                                                        )
                                                        .build()
                                        )
                                        .toList()
                        )

                        /*
                         * 선택한 월 현재 지출
                         */
                        .currentMonthAmount(
                                currentAmount
                        )

                        /*
                         * 현재까지 지난 날짜
                         */
                        .currentMonthDays(
                                currentDays
                        )

                        /*
                         * 해당 월 전체 날짜
                         */
                        .totalDaysInMonth(
                                totalDays
                        )

                        .build();


        /*
         * AI 예상 지출 계산
         */
        HomeAiResDTO.ExpectedBudgetResult result =
                budgetPredictionService.predict(
                        analysis
                );


        /*
         * 예상 지출 - 현재 지출
         */
        int remainingExpectedAmount =
                Math.max(
                        result.expectedAmount()
                                - currentAmount,
                        0
                );


        /*
         * 예상 지출 DB Entity 생성
         */
        ExpectedBudget expectedBudget =
                ExpectedBudget.builder()
                        .member(member)

                        /*
                         * 선택한 연도
                         */
                        .targetYear(
                                targetMonth.getYear()
                        )

                        /*
                         * 선택한 월
                         */
                        .targetMonth(
                                targetMonth.getMonthValue()
                        )

                        .expectedAmount(
                                result.expectedAmount()
                        )

                        .recommendedBudget(
                                result.recommendedBudget()
                        )

                        .currentAmount(
                                currentAmount
                        )

                        .remainingExpectedAmount(
                                remainingExpectedAmount
                        )

                        .reason(
                                result.reason()
                        )

                        .confidence(
                                result.confidence()
                        )

                        .analyzedMonths(
                                monthlyAmount.size()
                        )

                        .createdAt(
                                LocalDateTime.now()
                        )

                        .build();


        /*
         * DB 저장
         */
        expectedBudgetRepository.save(
                expectedBudget
        );


        /*
         * 응답
         */
        return HomeConverter.toResponse(
                expectedBudget
        );
    }
}