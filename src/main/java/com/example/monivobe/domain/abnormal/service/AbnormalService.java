package com.example.monivobe.domain.abnormal.service;

import com.example.monivobe.domain.abnormal.dto.AbnormalResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.dto.response.TransactionResDTO;
import com.example.monivobe.domain.transaction.entity.AbnormalTransaction;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.repository.AbnormalTransactionRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import com.example.monivobe.domain.transaction.service.TransactionOntologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AbnormalService {

    private final TransactionRepository transactionRepository;
    private final AbnormalTransactionRepository abnormalTransactionRepository;

    /*
     * ============================================================
     * Ontology
     * ============================================================
     */
    private final TransactionOntologyService transactionOntologyService;


    /**
     * ============================================================
     * 회원의 이상 지출 조회
     *
     * isAbnormal = true 인 거래만 조회
     * ============================================================
     */
    public List<AbnormalResDTO.AbnormalSpendingResDTO> getAbnormal(
            Member member
    ) {

        List<AbnormalTransaction> abnormalTransactions =
                abnormalTransactionRepository
                        .findByMemberAndTransaction_IsAbnormalTrue(member);

        List<AbnormalResDTO.AbnormalSpendingResDTO> result =
                new ArrayList<>();

        for (AbnormalTransaction abnormalTransaction
                : abnormalTransactions) {

            Transaction transaction =
                    abnormalTransaction.getTransaction();

            TransactionResDTO.TransactionOntologyContext ontologyContext =
                    transactionOntologyService.getTransactionContext(
                            transaction.getId()
                    );

            String categoryName =
                    getDisplayCategory(
                            transaction,
                            ontologyContext
                    );

            result.add(
                    AbnormalResDTO.AbnormalSpendingResDTO.builder()
                            .transactionId(transaction.getId())
                            .merchant(transaction.getMerchant())
                            .amount(transaction.getAmount())
                            .category(categoryName)
                            .date(transaction.getDate())
                            .score(abnormalTransaction.getScore())
                            .type("RULE")
                            .reason(abnormalTransaction.getReason())
                            .build()
            );
        }

        result.sort(
                Comparator.comparing(
                        AbnormalResDTO.AbnormalSpendingResDTO::score
                ).reversed()
        );

        return result;
    }


    /**
     * ============================================================
     * 새로운 거래 이상 지출 분석
     *
     * 새로운 거래를 분석한다.
     *
     * 중요:
     * isAbnormal 값이 true/false/null인지와 관계없이
     * 이상 지출 분석을 수행한다.
     * ============================================================
     */
    @Transactional
    public void analyzeNewTransactions(
            List<Transaction> newTransactions
    ) {

        /*
         * 새로운 거래가 없으면 종료
         */
        if (
                newTransactions == null
                        || newTransactions.isEmpty()
        ) {
            return;
        }


        /*
         * ========================================================
         * 회원
         * ========================================================
         */

        Member member =
                newTransactions
                        .get(0)
                        .getMember();


        /*
         * ========================================================
         * 전체 거래 조회
         *
         * 기존 거래 + 새로운 거래
         * ========================================================
         */

        List<Transaction> allTransactions =
                transactionRepository
                        .findByMemberOrderByDateDesc(member);


        /*
         * ========================================================
         * Ontology Context 미리 조회
         *
         * transactionId → Ontology Context
         *
         * 반복적인 Ontology 조회를 방지
         * ========================================================
         */

        Map<Long, TransactionResDTO.TransactionOntologyContext>
                ontologyContexts =
                loadOntologyContexts(
                        allTransactions
                );


        /*
         * ========================================================
         * 새로운 거래 분석
         *
         * isAbnormal 값과 관계없이 분석
         * ========================================================
         */

        for (Transaction transaction : newTransactions) {

            /*
             * 지출이 아니면 분석하지 않음
             */
            if (!isExpense(transaction)) {
                continue;
            }


            /*
             * ====================================================
             * 이상 지출 분석
             *
             * isAbnormal이 false여도 분석한다.
             * ====================================================
             */

            AbnormalResDTO.AnalysisResult analysis =
                    analyzeTransaction(
                            transaction,
                            allTransactions,
                            ontologyContexts
                    );


            /*
             * ====================================================
             * 60점 이상
             * ====================================================
             */

            if (analysis.score() >= 60) {

                /*
                 * Transaction 이상 지출 여부
                 */
                transaction.setIsAbnormal(true);


                /*
                 * AbnormalTransaction 저장
                 *
                 * 기존 데이터가 있으면 갱신
                 */
                saveAbnormalTransaction(
                        member,
                        transaction,
                        analysis
                );

            } else {

                /*
                 * =================================================
                 * 이상 지출 아님
                 * =================================================
                 */

                transaction.setIsAbnormal(false);
            }
        }
    }


    /**
     * ============================================================
     * Ontology Context 일괄 조회
     * ============================================================
     */
    private Map<Long, TransactionResDTO.TransactionOntologyContext>
    loadOntologyContexts(
            List<Transaction> transactions
    ) {

        Map<Long, TransactionResDTO.TransactionOntologyContext>
                contexts =
                new HashMap<>();

        for (Transaction transaction : transactions) {

            if (
                    transaction == null
                            || transaction.getId() == null
            ) {
                continue;
            }

            TransactionResDTO.TransactionOntologyContext context =
                    transactionOntologyService.getTransactionContext(
                            transaction.getId()
                    );

            if (context != null) {

                contexts.put(
                        transaction.getId(),
                        context
                );
            }
        }

        return contexts;
    }


    /**
     * ============================================================
     * 이상 지출 DB 저장
     *
     * 이미 등록된 거래라면
     * score / reason을 최신 분석 결과로 갱신한다.
     * ============================================================
     */
    private void saveAbnormalTransaction(
            Member member,
            Transaction transaction,
            AbnormalResDTO.AnalysisResult analysis
    ) {

        AbnormalTransaction abnormalTransaction =
                abnormalTransactionRepository
                        .findByMemberAndTransaction(
                                member,
                                transaction
                        )
                        .orElseGet(() ->
                                new AbnormalTransaction(
                                        member,
                                        transaction,
                                        analysis.reason(),
                                        analysis.score()
                                )
                        );


        /*
         * 최신 분석 결과로 갱신
         */
        abnormalTransaction.setReason(
                analysis.reason()
        );

        abnormalTransaction.setScore(
                analysis.score()
        );


        abnormalTransactionRepository.save(
                abnormalTransaction
        );
    }


    // ============================================================
    // 이상 지출 분석
    // ============================================================

    private AbnormalResDTO.AnalysisResult analyzeTransaction(
            Transaction transaction,
            List<Transaction> allTransactions,
            Map<Long, TransactionResDTO.TransactionOntologyContext>
                    ontologyContexts
    ) {

        int score = 0;

        List<String> reasons =
                new ArrayList<>();


        /*
         * ========================================================
         * 1. 고액 지출
         *
         * 온톨로지 소분류 기준 평균 금액을 우선 사용
         * ========================================================
         */

        double averageAmount =
                calculateOntologyAverageAmount(
                        transaction,
                        allTransactions,
                        ontologyContexts
                );


        /*
         * 온톨로지 기준 평균을 계산할 수 없으면
         * 기존 DB Category 기준으로 fallback
         */
        if (averageAmount <= 0) {

            averageAmount =
                    calculateAverageAmount(
                            transaction,
                            allTransactions
                    );
        }


        if (averageAmount > 0) {

            double ratio =
                    transaction.getAmount()
                            / averageAmount;


            if (ratio >= 5) {

                score += 40;

                reasons.add(
                        String.format(
                                "평소 동일 소비 유형의 평균 지출보다 약 %.1f배 높은 금액입니다.",
                                ratio
                        )
                );

            } else if (ratio >= 3) {

                score += 30;

                reasons.add(
                        String.format(
                                "평소 동일 소비 유형의 평균 지출보다 약 %.1f배 높은 금액입니다.",
                                ratio
                        )
                );

            } else if (ratio >= 2) {

                score += 20;

                reasons.add(
                        String.format(
                                "평소 동일 소비 유형의 평균 지출보다 약 %.1f배 높은 금액입니다.",
                                ratio
                        )
                );
            }
        }


        /*
         * ========================================================
         * 2. 신규 가맹점
         * ========================================================
         */

        if (
                isNewMerchant(
                        transaction,
                        allTransactions
                )
        ) {

            score += 30;

            reasons.add(
                    "기존에 이용 기록이 없는 가맹점입니다."
            );
        }


        /*
         * ========================================================
         * 3. 반복 소액 결제
         * ========================================================
         */

        if (
                isRepeatedSmallPayment(
                        transaction,
                        allTransactions
                )
        ) {

            score += 20;

            reasons.add(
                    "최근 동일 가맹점에서 소액 결제가 반복되고 있습니다."
            );
        }


        /*
         * ========================================================
         * 4. 온톨로지 기반 새로운 소비 유형
         *
         * DB에서는
         *
         * 식비
         *
         * 로 동일하지만
         *
         * Ontology에서는
         *
         * 카페
         * 배달
         * 외식
         *
         * 으로 구분한다.
         * ========================================================
         */

        if (
                isNewOntologyCategory(
                        transaction,
                        allTransactions,
                        ontologyContexts
                )
        ) {

            score += 20;

            String categoryName =
                    getOntologyCategoryName(
                            transaction,
                            ontologyContexts
                    );

            if (categoryName != null) {

                reasons.add(
                        String.format(
                                "기존에 이용하지 않던 새로운 소비 유형 '%s'에서 지출이 발생했습니다.",
                                categoryName
                        )
                );

            } else {

                reasons.add(
                        "기존에 이용하지 않던 새로운 소비 유형에서 지출이 발생했습니다."
                );
            }
        }


        /*
         * ========================================================
         * 5. 비정상 시간
         * ========================================================
         */

        if (
                isAbnormalTime(
                        transaction,
                        allTransactions
                )
        ) {

            score += 20;

            reasons.add(
                    "평소 소비하지 않는 시간대에 발생한 거래입니다."
            );
        }


        /*
         * ========================================================
         * 최대 100점
         * ========================================================
         */

        score =
                Math.min(
                        score,
                        100
                );


        String reason =
                reasons.isEmpty()
                        ? "이상 지출 패턴이 감지되었습니다."
                        : String.join(
                        " ",
                        reasons
                );


        return new AbnormalResDTO.AnalysisResult(
                score,
                reason
        );
    }


    // ============================================================
    // 온톨로지 기반 평균 지출
    // ============================================================

    private double calculateOntologyAverageAmount(
            Transaction target,
            List<Transaction> transactions,
            Map<Long, TransactionResDTO.TransactionOntologyContext>
                    ontologyContexts
    ) {

        /*
         * 대상 거래의 Ontology 정보
         */
        TransactionResDTO.TransactionOntologyContext targetContext =
                ontologyContexts.get(
                        target.getId()
                );


        /*
         * 온톨로지 정보가 없으면 fallback
         */
        if (
                targetContext == null
                        || targetContext.categoryName() == null
        ) {
            return 0;
        }


        String targetCategory =
                targetContext.categoryName();


        List<Transaction> previousTransactions =
                transactions.stream()

                        /*
                         * 자기 자신 제외
                         */
                        .filter(t ->
                                !t.getId()
                                        .equals(
                                                target.getId()
                                        )
                        )

                        /*
                         * 금액 존재
                         */
                        .filter(t ->
                                t.getAmount() != null
                        )

                        /*
                         * 날짜 존재
                         */
                        .filter(t ->
                                t.getDate() != null
                        )

                        /*
                         * 과거 거래만
                         */
                        .filter(t ->
                                t.getDate()
                                        .isBefore(
                                                target.getDate()
                                        )
                        )

                        /*
                         * 온톨로지 소분류가 동일한 거래만
                         */
                        .filter(t -> {

                            TransactionResDTO.TransactionOntologyContext
                                    context =
                                    ontologyContexts.get(
                                            t.getId()
                                    );

                            return context != null
                                    && context.categoryName() != null
                                    && context.categoryName()
                                    .equals(
                                            targetCategory
                                    );
                        })

                        .toList();


        if (
                previousTransactions.isEmpty()
        ) {
            return 0;
        }


        return previousTransactions.stream()

                .mapToInt(
                        Transaction::getAmount
                )

                .average()

                .orElse(0);
    }


    // ============================================================
    // 기존 DB Category 기준 평균
    // ============================================================

    private double calculateAverageAmount(
            Transaction target,
            List<Transaction> transactions
    ) {

        List<Transaction> previousTransactions =
                transactions.stream()

                        .filter(t ->
                                !t.getId()
                                        .equals(
                                                target.getId()
                                        )
                        )

                        .filter(t ->
                                t.getAmount() != null
                        )

                        .filter(t ->
                                t.getDate() != null
                        )

                        .filter(t ->
                                t.getDate()
                                        .isBefore(
                                                target.getDate()
                                        )
                        )

                        .filter(t -> {

                            if (
                                    target.getCategory()
                                            == null
                            ) {
                                return true;
                            }

                            return t.getCategory() != null
                                    && t.getCategory()
                                    .getId()
                                    .equals(
                                            target.getCategory()
                                                    .getId()
                                    );
                        })

                        .toList();


        if (
                previousTransactions.isEmpty()
        ) {
            return 0;
        }


        return previousTransactions.stream()

                .mapToInt(
                        Transaction::getAmount
                )

                .average()

                .orElse(0);
    }


    // ============================================================
    // 온톨로지 기반 새로운 소비 유형
    // ============================================================

    private boolean isNewOntologyCategory(
            Transaction target,
            List<Transaction> transactions,
            Map<Long, TransactionResDTO.TransactionOntologyContext>
                    ontologyContexts
    ) {

        TransactionResDTO.TransactionOntologyContext targetContext =
                ontologyContexts.get(
                        target.getId()
                );


        /*
         * 온톨로지 정보가 없으면
         * 판단하지 않는다.
         */
        if (
                targetContext == null
                        || targetContext.categoryName() == null
        ) {
            return false;
        }


        String targetCategory =
                targetContext.categoryName();


        /*
         * 과거에 동일한 소분류를 사용한 적이 있는지 확인
         */
        return transactions.stream()

                .filter(t ->
                        !t.getId()
                                .equals(
                                        target.getId()
                                )
                )

                .filter(t ->
                        t.getDate() != null
                )

                .filter(t ->
                        t.getDate()
                                .isBefore(
                                        target.getDate()
                                )
                )

                .map(t ->
                        ontologyContexts.get(
                                t.getId()
                        )
                )

                .filter(context ->
                        context != null
                                && context.categoryName() != null
                )

                .noneMatch(context ->
                        context.categoryName()
                                .equals(
                                        targetCategory
                                )
                );
    }


    // ============================================================
    // 온톨로지 소비 유형 이름
    // ============================================================

    private String getOntologyCategoryName(
            Transaction transaction,
            Map<Long, TransactionResDTO.TransactionOntologyContext>
                    ontologyContexts
    ) {

        TransactionResDTO.TransactionOntologyContext context =
                ontologyContexts.get(
                        transaction.getId()
                );

        if (context == null) {
            return null;
        }

        return context.categoryName();
    }


    // ============================================================
    // 신규 가맹점
    // ============================================================

    private boolean isNewMerchant(
            Transaction target,
            List<Transaction> transactions
    ) {

        if (
                target.getMerchant()
                        == null
        ) {
            return false;
        }


        String targetMerchant =
                target.getMerchant()
                        .trim();


        return transactions.stream()

                .filter(t ->
                        !t.getId()
                                .equals(
                                        target.getId()
                                )
                )

                .filter(t ->
                        t.getDate() != null
                )

                .filter(t ->
                        t.getDate()
                                .isBefore(
                                        target.getDate()
                                )
                )

                .noneMatch(t ->
                        t.getMerchant() != null
                                && t.getMerchant()
                                .trim()
                                .equalsIgnoreCase(
                                        targetMerchant
                                )
                );
    }


    // ============================================================
    // 반복 소액 결제
    // ============================================================

    private boolean isRepeatedSmallPayment(
            Transaction target,
            List<Transaction> transactions
    ) {

        if (
                target.getMerchant() == null
                        || target.getAmount() == null
                        || target.getDate() == null
        ) {
            return false;
        }


        /*
         * 10,000원 초과는 소액 결제로 보지 않음
         */
        if (
                target.getAmount() > 10000
        ) {
            return false;
        }


        LocalDateTime start =
                target.getDate()
                        .minusDays(30);


        long count =
                transactions.stream()

                        .filter(t ->
                                !t.getId()
                                        .equals(
                                                target.getId()
                                        )
                        )

                        .filter(t ->
                                t.getMerchant() != null
                        )

                        .filter(t ->
                                t.getMerchant()
                                        .trim()
                                        .equalsIgnoreCase(
                                                target.getMerchant()
                                                        .trim()
                                        )
                        )

                        .filter(t ->
                                t.getAmount() != null
                        )

                        .filter(t ->
                                t.getAmount() <= 10000
                        )

                        .filter(t ->
                                t.getDate() != null
                        )

                        .filter(t ->
                                !t.getDate()
                                        .isBefore(
                                                start
                                        )
                                        && t.getDate()
                                        .isBefore(
                                                target.getDate()
                                        )
                        )

                        .count();


        return count >= 3;
    }


    // ============================================================
    // 기존 DB 기준 새로운 카테고리
    // ============================================================

    private boolean isNewCategory(
            Transaction target,
            List<Transaction> transactions
    ) {

        if (
                target.getCategory() == null
                        || target.getDate() == null
        ) {
            return false;
        }


        Long categoryId =
                target.getCategory()
                        .getId();


        return transactions.stream()

                .filter(t ->
                        !t.getId()
                                .equals(
                                        target.getId()
                                )
                )

                .filter(t ->
                        t.getDate() != null
                )

                .filter(t ->
                        t.getDate()
                                .isBefore(
                                        target.getDate()
                                )
                )

                .filter(t ->
                        t.getCategory() != null
                )

                .noneMatch(t ->
                        t.getCategory()
                                .getId()
                                .equals(
                                        categoryId
                                )
                );
    }


    // ============================================================
    // 비정상 시간
    // ============================================================

    private boolean isAbnormalTime(
            Transaction target,
            List<Transaction> transactions
    ) {

        if (
                target.getDate() == null
        ) {
            return false;
        }


        int hour =
                target.getDate()
                        .getHour();


        return hour >= 0
                && hour < 5;
    }


    // ============================================================
    // 지출 여부
    // ============================================================

    private boolean isExpense(
            Transaction transaction
    ) {

        if (
                transaction.getTransactionType()
                        == null
        ) {
            return false;
        }


        return "EXPENSE".equals(
                transaction
                        .getTransactionType()
                        .name()
        );
    }


    // ============================================================
    // 이상 지출 확인 완료
    // ============================================================

    @Transactional
    public Object updateAbnormal(
            Long transactionId,
            Member member
    ) {

        Transaction transaction =
                transactionRepository
                        .findByIdAndMember(
                                transactionId,
                                member
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "해당 거래 내역을 찾을 수 없습니다."
                                )
                        );


        /*
         * 이상 지출 해제
         */
        transaction.setIsAbnormal(false);


        /*
         * Transaction ID 반환
         */
        return transaction.getId();
    }


    // ============================================================
    // 화면에 보여줄 카테고리
    // ============================================================

    private String getDisplayCategory(
            Transaction transaction,
            TransactionResDTO.TransactionOntologyContext context
    ) {

        /*
         * Ontology 소분류가 존재하면 소분류 사용
         *
         * 예:
         * 카페
         * 배달
         * 외식
         */
        if (
                context != null
                        && context.categoryName() != null
                        && !context.categoryName().isBlank()
        ) {

            return context.categoryName();
        }


        /*
         * Ontology 정보가 없으면
         * 기존 DB Category 사용
         */
        if (
                transaction.getCategory() != null
        ) {

            return transaction
                    .getCategory()
                    .getName();
        }


        return "미분류";
    }
}