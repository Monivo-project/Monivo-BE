package com.example.monivobe.domain.abnormal.service;

import com.example.monivobe.domain.abnormal.dto.AbnormalResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.AbnormalTransaction;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.repository.AbnormalTransactionRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AbnormalService {

    private final TransactionRepository transactionRepository;
    private final AbnormalTransactionRepository abnormalTransactionRepository;


    /**
     * ============================================================
     * 회원의 이상 지출 조회
     *
     * 페이지 조회 시에는 "분석"하지 않고
     * 이미 분석된 isAbnormal = true 거래만 조회하는 것을
     * 권장합니다.
     * ============================================================
     */
    public List<AbnormalResDTO.AbnormalSpendingResDTO> getAbnormal(
            Member member
    ) {

        List<Transaction> transactions =
                transactionRepository.findByMemberOrderByDateDesc(member);

        List<AbnormalResDTO.AbnormalSpendingResDTO> result =
                new ArrayList<>();

        for (Transaction transaction : transactions) {

            /*
             * 지출만 사용
             */
            if (!isExpense(transaction)) {
                continue;
            }

            /*
             * 이미 이상 지출로 판정된 거래만 조회
             */
            if (!Boolean.TRUE.equals(
                    transaction.getIsAbnormal()
            )) {
                continue;
            }

            /*
             * 저장되어 있는 이상 지출 정보 조회
             */
            AbnormalTransaction abnormalTransaction =
                    abnormalTransactionRepository
                            .findByMemberAndTransaction(
                                    member,
                                    transaction
                            )
                            .orElse(null);

            /*
             * 이상 지출 정보가 없다면 넘어감
             */
            if (abnormalTransaction == null) {
                continue;
            }

            result.add(
                    AbnormalResDTO.AbnormalSpendingResDTO.builder()
                            .transactionId(
                                    transaction.getId()
                            )
                            .merchant(
                                    transaction.getMerchant()
                            )
                            .amount(
                                    transaction.getAmount()
                            )
                            .category(
                                    transaction.getCategory() != null
                                            ? transaction.getCategory().getName()
                                            : "미분류"
                            )
                            .date(
                                    transaction.getDate()
                            )
                            .score(
                                    abnormalTransaction.getScore()
                            )
                            .type("RULE")
                            .reason(
                                    abnormalTransaction.getReason()
                            )
                            .build()
            );
        }

        /*
         * 높은 점수부터 정렬
         */
        result.sort(
                Comparator.comparing(
                        AbnormalResDTO.AbnormalSpendingResDTO::score
                ).reversed()
        );

        return result;
    }


    /**
     * ============================================================
     * 새로운 거래만 이상 지출 분석
     *
     * TransactionProcessingService에서
     *
     * List<Transaction> newTransactions
     *
     * 를 받아서 처리한다.
     *
     * 새 거래만 분석하지만,
     * 이상 여부 판단을 위해 기존 거래도 함께 참고한다.
     * ============================================================
     */
    @Transactional
    public void analyzeNewTransactions(
            List<Transaction> newTransactions
    ) {

        /*
         * 새로운 거래가 없으면 아무것도 하지 않음
         */
        if (
                newTransactions == null
                        || newTransactions.isEmpty()
        ) {
            return;
        }


        /*
         * ========================================================
         * 회원 조회
         *
         * 새 거래의 member를 기준으로 전체 거래를 가져온다.
         * ========================================================
         */

        Member member =
                newTransactions
                        .get(0)
                        .getMember();


        /*
         * ========================================================
         * 기존 거래 + 새로운 거래
         *
         * 이상 여부 판단을 위해 전체 거래를 가져온다.
         * ========================================================
         */

        List<Transaction> allTransactions =
                transactionRepository
                        .findByMemberOrderByDateDesc(member);


        /*
         * ========================================================
         * 새로운 거래만 분석
         * ========================================================
         */

        for (Transaction transaction : newTransactions) {

            /*
             * 지출이 아니면 이상 지출 분석하지 않음
             */
            if (!isExpense(transaction)) {
                continue;
            }


            /*
             * 이미 분석된 거래라면 다시 분석하지 않음
             */
            if (
                    transaction.getIsAbnormal() != null
                            && transaction.getIsAbnormal()
            ) {
                continue;
            }


            /*
             * 이상 지출 분석
             */
            AbnormalResDTO.AnalysisResult analysis =
                    analyzeTransaction(
                            transaction,
                            allTransactions
                    );


            /*
             * ====================================================
             * 60점 이상
             * ====================================================
             */

            if (analysis.score() >= 60) {

                /*
                 * Transaction 이상 지출 여부 변경
                 */
                transaction.setIsAbnormal(true);


                /*
                 * AbnormalTransaction 저장
                 */
                saveAbnormalTransaction(
                        member,
                        transaction,
                        analysis
                );

            } else {

                /*
                 * 이상 지출이 아니면 false
                 */
                transaction.setIsAbnormal(false);
            }
        }
    }


    /**
     * ============================================================
     * 이상 지출 DB 저장
     *
     * 이미 등록된 거래라면 중복 저장하지 않는다.
     * ============================================================
     */
    private void saveAbnormalTransaction(
            Member member,
            Transaction transaction,
            AbnormalResDTO.AnalysisResult analysis
    ) {

        boolean alreadyExists =
                abnormalTransactionRepository
                        .findByMemberAndTransaction(
                                member,
                                transaction
                        )
                        .isPresent();

        if (alreadyExists) {
            return;
        }


        AbnormalTransaction abnormalTransaction =
                new AbnormalTransaction(
                        member,
                        transaction,
                        analysis.reason(),
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
            List<Transaction> allTransactions
    ) {

        int score = 0;

        List<String> reasons =
                new ArrayList<>();


        /*
         * ========================================================
         * 1. 고액 지출
         * ========================================================
         */

        double averageAmount =
                calculateAverageAmount(
                        transaction,
                        allTransactions
                );


        if (averageAmount > 0) {

            double ratio =
                    transaction.getAmount()
                            / averageAmount;


            if (ratio >= 5) {

                score += 40;

                reasons.add(
                        String.format(
                                "평소 평균 지출보다 약 %.1f배 높은 금액입니다.",
                                ratio
                        )
                );

            } else if (ratio >= 3) {

                score += 30;

                reasons.add(
                        String.format(
                                "평소 평균 지출보다 약 %.1f배 높은 금액입니다.",
                                ratio
                        )
                );

            } else if (ratio >= 2) {

                score += 20;

                reasons.add(
                        String.format(
                                "평소 평균 지출보다 약 %.1f배 높은 금액입니다.",
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
         * 4. 새로운 카테고리
         * ========================================================
         */

        if (
                isNewCategory(
                        transaction,
                        allTransactions
                )
        ) {

            score += 20;

            reasons.add(
                    "기존에 자주 이용하지 않던 소비 카테고리입니다."
            );
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
         * 최대 100점
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
    // 평균 지출 금액
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
    // 새로운 카테고리
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
}