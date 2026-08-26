package com.example.monivobe.domain.settings.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.settings.dto.SettingsResDTO;
import com.example.monivobe.domain.transaction.entity.Subscription;
import com.example.monivobe.domain.transaction.entity.SubscriptionTransaction;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.Status;
import com.example.monivobe.domain.transaction.repository.SubscriptionRepository;
import com.example.monivobe.domain.transaction.repository.SubscriptionTransactionRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SettingsService {

    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionTransactionRepository subscriptionTransactionRepository;


    // ============================================================
    // 정기결제 후보 조회
    //
    // PENDING 상태의 Subscription만 후보로 반환
    //
    // CONFIRMED  -> 현재 정기결제
    // DISMISSED  -> 등록하지 않은 결제
    // PENDING    -> 정기결제 후보
    // ============================================================

    public SettingsResDTO.GetCandidates getCandidates(Member member) {

        /*
         * --------------------------------------------------------
         * 1. 기존 PENDING Subscription 조회
         * --------------------------------------------------------
         *
         * 이미 후보로 생성된 가맹점은 여기서 바로 가져온다.
         *
         * 즉,
         *
         * PENDING 가맹점 -> 후보
         * CONFIRMED 가맹점 -> 후보 아님
         * DISMISSED 가맹점 -> 후보 아님
         */
        List<Subscription> pendingSubscriptions =
                subscriptionRepository.findByMemberAndStatus(
                        member,
                        Status.PENDING
                );

        /*
         * 현재 PENDING 후보를 merchant 기준으로 관리
         *
         * 이후 새로운 거래내역을 분석했을 때
         * 이미 PENDING인 가맹점은 새 Subscription을 만들지 않는다.
         */
        Map<String, Subscription> pendingSubscriptionMap =
                pendingSubscriptions.stream()
                        .filter(subscription ->
                                subscription.getMerchant() != null
                        )
                        .collect(Collectors.toMap(
                                Subscription::getMerchant,
                                subscription -> subscription,
                                (existing, duplicate) -> existing
                        ));


        /*
         * --------------------------------------------------------
         * 2. 거래내역 조회
         * --------------------------------------------------------
         */
        List<Transaction> transactions =
                transactionRepository.findByMemberOrderByDateDesc(member);


        /*
         * --------------------------------------------------------
         * 3. 가맹점별 거래내역 그룹화
         * --------------------------------------------------------
         */
        Map<String, List<Transaction>> groupedTransactions =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getMerchant() != null
                                        && !transaction.getMerchant().isBlank()
                        )
                        .collect(Collectors.groupingBy(
                                Transaction::getMerchant
                        ));


        /*
         * 최종 후보
         */
        List<SettingsResDTO.Candidates> candidates =
                new ArrayList<>();


        /*
         * --------------------------------------------------------
         * 4. 가맹점별 정기결제 분석
         * --------------------------------------------------------
         */
        for (Map.Entry<String, List<Transaction>> entry
                : groupedTransactions.entrySet()) {

            String merchant = entry.getKey();

            List<Transaction> merchantTransactions =
                    entry.getValue();


            /*
             * 날짜순 정렬
             *
             * 최신 거래가 먼저 오도록 정렬
             */
            merchantTransactions.sort(
                    Comparator.comparing(
                            Transaction::getDate
                    ).reversed()
            );


            /*
             * 최소 2회 이상 결제된 가맹점만 후보 분석
             */
            if (merchantTransactions.size() < 2) {
                continue;
            }


            /*
             * ----------------------------------------------------
             * 이미 Subscription이 존재하는지 확인
             * ----------------------------------------------------
             *
             * PENDING
             * CONFIRMED
             * DISMISSED
             *
             * 어떤 상태든 Subscription이 존재한다면
             * 새로 생성하지 않는다.
             */
            Optional<Subscription> existingSubscription =
                    subscriptionRepository
                            .findByMemberAndMerchant(
                                    member,
                                    merchant
                            );


            /*
             * 이미 존재하는 Subscription이 있다면
             *
             * PENDING  -> 후보에 표시
             * CONFIRMED -> 후보에서 제외
             * DISMISSED -> 후보에서 제외
             */
            if (existingSubscription.isPresent()) {

                Subscription subscription =
                        existingSubscription.get();

                /*
                 * PENDING이 아니면 후보에서 제외
                 */
                if (subscription.getStatus() != Status.PENDING) {
                    continue;
                }

                /*
                 * PENDING이면 기존 후보를 사용
                 */
                candidates.add(
                        buildCandidateResponse(
                                subscription,
                                merchantTransactions
                        )
                );

                continue;
            }


            /*
             * ----------------------------------------------------
             * 아직 Subscription이 없는 가맹점
             * ----------------------------------------------------
             *
             * 정기결제 패턴인지 확인
             */
            String billingCycle =
                    determineBillingCycle(
                            merchantTransactions
                    );


            /*
             * 정기결제가 아니면 후보에서 제외
             */
            if (billingCycle == null) {
                continue;
            }


            /*
             * 평균 결제금액
             */
            int averageAmount =
                    (int) merchantTransactions.stream()
                            .map(Transaction::getAmount)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .average()
                            .orElse(0);


            /*
             * 다음 결제일 예상
             */
            LocalDate nextPaymentDate =
                    calculateNextPaymentDate(
                            merchantTransactions,
                            billingCycle
                    );


            /*
             * ----------------------------------------------------
             * 새로운 PENDING Subscription 생성
             * ----------------------------------------------------
             */
            Subscription subscription =
                    createPendingSubscription(
                            member,
                            merchant,
                            merchantTransactions,
                            averageAmount,
                            billingCycle,
                            nextPaymentDate
                    );


            /*
             * 후보 목록에 추가
             */
            candidates.add(
                    buildCandidateResponse(
                            subscription,
                            merchantTransactions
                    )
            );
        }


        /*
         * --------------------------------------------------------
         * 5. 최종 후보 반환
         * --------------------------------------------------------
         */
        return SettingsResDTO.GetCandidates.builder()
                .candidates(candidates)
                .build();
    }


    // ============================================================
    // 후보 응답 DTO 생성
    // ============================================================

    private SettingsResDTO.Candidates buildCandidateResponse(
            Subscription subscription,
            List<Transaction> transactions
    ) {

        Integer averageAmount =
                subscription.getAverageAmount();


        /*
         * averageAmount가 없는 경우
         * 거래내역으로 다시 계산
         */
        if (averageAmount == null) {

            averageAmount =
                    (int) transactions.stream()
                            .map(Transaction::getAmount)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .average()
                            .orElse(0);
        }


        return SettingsResDTO.Candidates.builder()
                .candidateId(subscription.getId())
                .merchant(subscription.getMerchant())
                .transactionCount(transactions.size())
                .averageAmount(averageAmount)
                .billingCycle(subscription.getBillingCycle())
                .nextPaymentDate(
                        subscription.getNext_payment_date()
                )
                .build();
    }


    // ============================================================
    // 후보의 결제내역 조회
    // ============================================================

    public SettingsResDTO.GetCandidatesDetail getCandidatesDetail(
            Long candidateId,
            Member member
    ) {

        Subscription subscription =
                subscriptionRepository
                        .findByIdAndMember(candidateId, member)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "정기결제를 찾을 수 없습니다."
                                )
                        );

        /*
         * PENDING / CONFIRMED / DISMISSED
         * 모든 상태의 정기결제 상세 조회를 허용한다.
         */

        List<SubscriptionTransaction> relations =
                subscriptionTransactionRepository
                        .findBySubscription(subscription);

        List<SettingsResDTO.TransactionDetail> transactions =
                relations.stream()
                        .map(SubscriptionTransaction::getTransaction)
                        .sorted(
                                Comparator.comparing(
                                        Transaction::getDate
                                ).reversed()
                        )
                        .map(transaction ->
                                SettingsResDTO.TransactionDetail
                                        .builder()
                                        .transactionId(
                                                transaction.getId()
                                        )
                                        .date(
                                                transaction
                                                        .getDate()
                                                        .toLocalDate()
                                        )
                                        .amount(
                                                transaction.getAmount()
                                        )
                                        .build()
                        )
                        .toList();

        return SettingsResDTO.GetCandidatesDetail.builder()
                .candidateId(subscription.getId())
                .merchant(subscription.getMerchant())
                .transactionCount(transactions.size())
                .averageAmount(subscription.getAverageAmount())
                .billingCycle(subscription.getBillingCycle())
                .nextPaymentDate(
                        subscription.getNext_payment_date()
                )
                .transactions(transactions)
                .build();
    }


    // ============================================================
    // 정기결제로 등록
    //
    // PENDING -> CONFIRMED
    // ============================================================

    @Transactional
    public Subscription createCandidates(
            Long candidateId,
            Member member
    ) {

        Subscription subscription =
                subscriptionRepository
                        .findByIdAndMember(candidateId, member)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "정기결제 후보를 찾을 수 없습니다."
                                )
                        );


        /*
         * PENDING 상태에서만 등록 가능
         */
        if (subscription.getStatus() != Status.PENDING && subscription.getStatus() != Status.DISMISSED) {

            throw new IllegalArgumentException(
                    "현재 정기결제 후보가 아닙니다."
            );
        }


        subscription.setStatus(Status.CONFIRMED);
        subscription.setIsActive(true);


        return subscription;
    }


    // ============================================================
    // 정기결제로 미등록
    //
    // PENDING -> DISMISSED
    //
    // 이후 getCandidates()에서는 다시 나오지 않는다.
    // ============================================================

    @Transactional
    public Subscription createCandidatesDismiss(
            Long candidateId,
            Member member
    ) {

        Subscription subscription =
                subscriptionRepository
                        .findByIdAndMember(candidateId, member)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "정기결제 후보를 찾을 수 없습니다."
                                )
                        );


        /*
         * PENDING 상태에서만 미등록 처리 가능
         */
        if (subscription.getStatus() != Status.PENDING && subscription.getStatus() != Status.CONFIRMED) {

            throw new IllegalArgumentException(
                    "현재 정기결제 후보가 아닙니다."
            );
        }


        /*
         * PENDING -> DISMISSED
         */
        subscription.setStatus(Status.DISMISSED);
        subscription.setIsActive(false);


        return subscription;
    }


    // ============================================================
    // 새로운 PENDING 후보 생성
    // ============================================================

    private Subscription createPendingSubscription(
            Member member,
            String merchant,
            List<Transaction> transactions,
            int averageAmount,
            String billingCycle,
            LocalDate nextPaymentDate
    ) {

        Subscription subscription =
                Subscription.builder()
                        .member(member)
                        .merchant(merchant)
                        .averageAmount(averageAmount)
                        .billingCycle(billingCycle)
                        .next_payment_date(nextPaymentDate)
                        .isActive(false)
                        .status(Status.PENDING)
                        .build();


        /*
         * Subscription 저장
         */
        subscriptionRepository.save(subscription);


        /*
         * 실제 거래내역 연결
         */
        for (Transaction transaction : transactions) {

            SubscriptionTransaction relation =
                    SubscriptionTransaction.builder()
                            .subscription(subscription)
                            .transaction(transaction)
                            .build();

            subscriptionTransactionRepository.save(relation);
        }


        return subscription;
    }


    // ============================================================
    // 정기결제 주기 판단
    // ============================================================

    private String determineBillingCycle(
            List<Transaction> transactions
    ) {

        if (transactions.size() < 2) {
            return null;
        }


        /*
         * 최근 거래 최대 6개 사용
         */
        List<Transaction> sorted =
                transactions.stream()
                        .sorted(
                                Comparator.comparing(
                                        Transaction::getDate
                                )
                        )
                        .limit(6)
                        .toList();


        if (sorted.size() < 2) {
            return null;
        }


        List<Long> intervals =
                new ArrayList<>();


        for (int i = 1; i < sorted.size(); i++) {

            LocalDate previous =
                    sorted.get(i - 1)
                            .getDate()
                            .toLocalDate();


            LocalDate current =
                    sorted.get(i)
                            .getDate()
                            .toLocalDate();


            long days =
                    ChronoUnit.DAYS.between(
                            previous,
                            current
                    );


            intervals.add(days);
        }


        double averageInterval =
                intervals.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);


        /*
         * 월간
         *
         * 약 25 ~ 35일
         */
        if (averageInterval >= 25
                && averageInterval <= 35) {

            return "MONTHLY";
        }


        /*
         * 연간
         *
         * 약 330 ~ 400일
         */
        if (averageInterval >= 330
                && averageInterval <= 400) {

            return "YEARLY";
        }


        return null;
    }


    // ============================================================
    // 다음 결제일 계산
    // ============================================================

    private LocalDate calculateNextPaymentDate(
            List<Transaction> transactions,
            String billingCycle
    ) {

        Transaction latest =
                transactions.stream()
                        .max(
                                Comparator.comparing(
                                        Transaction::getDate
                                )
                        )
                        .orElseThrow();


        LocalDate latestDate =
                latest.getDate().toLocalDate();


        if ("MONTHLY".equals(billingCycle)) {

            return latestDate.plusMonths(1);
        }


        if ("YEARLY".equals(billingCycle)) {

            return latestDate.plusYears(1);
        }


        return latestDate;
    }


    // ============================================================
    // 현재 정기결제 조회
    //
    // CONFIRMED 상태
    // ============================================================

    @Transactional(readOnly = true)
    public SettingsResDTO.GetCandidates getSubscriptions(
            Member member
    ) {

        List<Subscription> subscriptions =
                subscriptionRepository.findByMemberAndStatus(
                        member,
                        Status.CONFIRMED
                );


        return createSubscriptionResponse(
                subscriptions
        );
    }


    // ============================================================
    // 정기결제 미등록 조회
    //
    // DISMISSED 상태
    // ============================================================

    @Transactional(readOnly = true)
    public SettingsResDTO.GetCandidates getSubscriptionsDismissed(
            Member member
    ) {

        List<Subscription> subscriptions =
                subscriptionRepository.findByMemberAndStatus(
                        member,
                        Status.DISMISSED
                );


        return createSubscriptionResponse(
                subscriptions
        );
    }


    // ============================================================
    // Subscription -> GetCandidates 변환
    // ============================================================

    private SettingsResDTO.GetCandidates createSubscriptionResponse(
            List<Subscription> subscriptions
    ) {

        List<SettingsResDTO.Candidates> candidates =
                subscriptions.stream()
                        .map(subscription -> {

                            /*
                             * 해당 정기결제에 연결된 거래 조회
                             */
                            List<SubscriptionTransaction>
                                    subscriptionTransactions =
                                    subscriptionTransactionRepository
                                            .findBySubscription(
                                                    subscription
                                            );


                            /*
                             * 연결된 거래 수
                             */
                            int transactionCount =
                                    subscriptionTransactions.size();


                            /*
                             * 평균 결제금액
                             */
                            Integer averageAmount =
                                    subscription.getAverageAmount();


                            /*
                             * averageAmount가 없는 경우
                             * 연결된 Transaction으로 계산
                             */
                            if (averageAmount == null
                                    && !subscriptionTransactions.isEmpty()) {

                                averageAmount =
                                        (int) subscriptionTransactions
                                                .stream()
                                                .map(
                                                        SubscriptionTransaction
                                                                ::getTransaction
                                                )
                                                .map(
                                                        Transaction::getAmount
                                                )
                                                .filter(
                                                        Objects::nonNull
                                                )
                                                .mapToInt(
                                                        Integer::intValue
                                                )
                                                .average()
                                                .orElse(0);
                            }


                            /*
                             * 다음 결제일
                             */
                            LocalDate nextPaymentDate =
                                    subscription
                                            .getNext_payment_date();


                            /*
                             * 정기결제 주기
                             */
                            String billingCycle =
                                    subscription
                                            .getBillingCycle();


                            return SettingsResDTO.Candidates
                                    .builder()
                                    .candidateId(
                                            subscription.getId()
                                    )
                                    .merchant(
                                            subscription.getMerchant()
                                    )
                                    .transactionCount(
                                            transactionCount
                                    )
                                    .averageAmount(
                                            averageAmount != null
                                                    ? averageAmount
                                                    : 0
                                    )
                                    .billingCycle(
                                            billingCycle
                                    )
                                    .nextPaymentDate(
                                            nextPaymentDate
                                    )
                                    .build();
                        })
                        .toList();


        return SettingsResDTO.GetCandidates.builder()
                .candidates(candidates)
                .build();
    }
}