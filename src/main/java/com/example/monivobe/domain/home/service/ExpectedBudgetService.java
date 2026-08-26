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

import java.time.LocalDate;
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
     * 데이터가 이미 있으면 DB 조회
     *
     * 데이터가 없으면 AI 분석 후 생성
     */
    @Transactional
    public HomeResDTO.ExpectedBudget getExpectedBudget(
            Member member,
            Integer year,
            Integer month
    ) {

        YearMonth targetMonth =
                YearMonth.of(year, month);

        Optional<ExpectedBudget> budget =
                expectedBudgetRepository
                        .findByMemberAndTargetYearAndTargetMonth(
                                member,
                                targetMonth.getYear(),
                                targetMonth.getMonthValue()
                        );

        /*
         * 이미 존재하면 DB 데이터 반환
         */
        if (budget.isPresent()) {

            return HomeConverter.toResponse(
                    budget.get()
            );
        }

        /*
         * 없으면 생성
         */
        return createExpectedBudget(
                member,
                targetMonth
        );
    }


    /**
     * 파일 업로드 후 예상 지출 생성
     *
     * 이미 존재하는 월이라면 다시 AI를 호출하지 않는다.
     *
     * 따라서 Excel을 여러 번 업로드해도
     * 동일한 월의 예상 지출이 계속 INSERT되지 않는다.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void createExpectedBudgetIfNotExists(
            Member member,
            YearMonth targetMonth
    ) {

        /*
         * 이미 존재하는 예상 지출인지 확인
         */
        Optional<ExpectedBudget> existing =
                expectedBudgetRepository
                        .findByMemberAndTargetYearAndTargetMonth(
                                member,
                                targetMonth.getYear(),
                                targetMonth.getMonthValue()
                        );

        if (existing.isPresent()) {

            System.out.println(
                    "[EXPECTED BUDGET] "
                            + targetMonth
                            + " 이미 존재함"
            );

            return;
        }

        /*
         * 예상 지출 생성
         */
        createExpectedBudget(
                member,
                targetMonth
        );
    }


    /**
     * 선택한 월의 예상 지출 생성
     */
    @Transactional
    public HomeResDTO.ExpectedBudget createExpectedBudget(
            Member member,
            YearMonth targetMonth
    ) {

        /*
         * ====================================================
         * AI 분석 기간
         *
         * 선택한 월 기준
         * 이전 6개월 + 선택한 월
         * ====================================================
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
         * ====================================================
         * 거래내역 조회
         * ====================================================
         */

        List<Transaction> transactions =
                transactionRepository
                        .findByMemberAndDateBetweenOrderByDateAsc(
                                member,
                                startDate,
                                endDate
                        );


        /*
         * ====================================================
         * 월별 지출
         *
         * EXPENSE만 계산
         * ====================================================
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
         * ====================================================
         * 카테고리별 지출
         *
         * EXPENSE만 계산
         * ====================================================
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
         * ====================================================
         * 선택한 월의 현재 지출
         * ====================================================
         */

        int currentAmount =
                monthlyAmount.getOrDefault(
                        targetMonth,
                        0
                );


        /*
         * ====================================================
         * 현재까지 경과한 날짜
         * ====================================================
         */

        YearMonth currentMonth =
                YearMonth.now();

        int currentDays;

        if (targetMonth.equals(currentMonth)) {

            currentDays =
                    LocalDate.now()
                            .getDayOfMonth();

        } else {

            currentDays =
                    targetMonth.lengthOfMonth();
        }


        /*
         * ====================================================
         * 해당 월 전체 날짜
         * ====================================================
         */

        int totalDays =
                targetMonth.lengthOfMonth();


        /*
         * ====================================================
         * AI 분석 요청 데이터
         * ====================================================
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
                         * 현재까지 경과한 날짜
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
         * ====================================================
         * AI 예상 지출
         * ====================================================
         */

        HomeAiResDTO.ExpectedBudgetResult result =
                budgetPredictionService.predict(
                        analysis
                );


        /*
         * ====================================================
         * 예상 지출 - 현재 지출
         * ====================================================
         */

        int remainingExpectedAmount =
                Math.max(
                        result.expectedAmount()
                                - currentAmount,
                        0
                );


        /*
         * ====================================================
         * ExpectedBudget Entity 생성
         * ====================================================
         */

        ExpectedBudget expectedBudget =
                ExpectedBudget.builder()

                        .member(member)

                        .targetYear(
                                targetMonth.getYear()
                        )

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
         * ====================================================
         * DB 저장
         * ====================================================
         */

        expectedBudgetRepository.save(
                expectedBudget
        );


        /*
         * ====================================================
         * 응답
         * ====================================================
         */

        return HomeConverter.toResponse(
                expectedBudget
        );
    }

    @Transactional
    public HomeResDTO.ExpectedBudget refreshExpectedBudget(
            Member member,
            YearMonth targetMonth
    ) {

        Optional<ExpectedBudget> existingBudget =
                expectedBudgetRepository
                        .findByMemberAndTargetYearAndTargetMonth(
                                member,
                                targetMonth.getYear(),
                                targetMonth.getMonthValue()
                        );

        if (existingBudget.isPresent()) {

            expectedBudgetRepository.delete(
                    existingBudget.get()
            );

            expectedBudgetRepository.flush();
        }

        return createExpectedBudget(
                member,
                targetMonth
        );
    }
}