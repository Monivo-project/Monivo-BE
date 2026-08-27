package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.abnormal.service.AbnormalService;
import com.example.monivobe.domain.home.service.ExpectedBudgetService;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.domain.settings.service.SettingsService;
import com.example.monivobe.domain.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessingService {

    private final TransactionImportService transactionImportService;

    private final TransactionClassificationService
            transactionClassificationService;

    private final ExpectedBudgetService
            expectedBudgetService;

    private final AbnormalService
            abnormalService;

    private final SettingsService
            settingsService;

    private final MemberRepository memberRepository;


    /**
     * ============================================================
     * 거래내역 전체 처리
     * ============================================================
     */
     @Transactional
    public List<Long> process(
            Long memberId,
            String fileKey
    ) {

        log.info("==================================================");
        log.info("거래내역 전체 처리 시작");
        log.info("memberId={}, fileKey={}", memberId, fileKey);
        log.info("==================================================");


        /*
         * ========================================================
         * 1단계
         * Excel → Transaction 저장
         * ========================================================
         */

        log.info("[1단계] 거래내역 import 시작");

        List<Transaction> transactions =
                transactionImportService.importTransactions(
                        memberId,
                        fileKey
                );

        log.info(
                "[1단계] 거래내역 import 완료 - count={}",
                transactions == null
                        ? 0
                        : transactions.size()
        );


        /*
         * ========================================================
         * 저장된 거래내역이 없는 경우
         * ========================================================
         */

        if (
                transactions == null
                        || transactions.isEmpty()
        ) {

            log.info(
                    "새롭게 저장된 거래내역이 없습니다."
            );

            return List.of();
        }


        /*
         * ========================================================
         * Transaction ID 추출
         * ========================================================
         */

        List<Long> transactionIds =
                transactions.stream()
                        .map(Transaction::getId)
                        .toList();

        log.info(
                "처리 대상 Transaction IDs={}",
                transactionIds
        );


        /*
         * ========================================================
         * 2단계
         * Merchant + Keyword + LLM 분류
         * ========================================================
         */

        log.info(
                "=================================================="
        );

        log.info(
                "[2단계] 거래 분류 시작"
        );


        transactionClassificationService.classify(
                transactionIds
        );


        log.info(
                "[2단계] 거래 분류 완료"
        );


        /*
         * ========================================================
         * Member 조회
         * ========================================================
         */

        log.info(
                "Member 조회 시작 - memberId={}",
                memberId
        );


        Member member =
                memberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "회원을 찾을 수 없습니다. memberId="
                                                + memberId
                                )
                        );


        log.info(
                "Member 조회 완료 - memberId={}",
                memberId
        );


        /*
         * ========================================================
         * 3단계
         * 예상 지출 생성
         * ========================================================
         */

        log.info(
                "=================================================="
        );

        log.info(
                "[3단계] 예상 지출 생성 시작"
        );


        expectedBudgetService.createExpectedBudgetIfNotExists(
                member,
                YearMonth.now()
        );


        log.info(
                "[3단계] 예상 지출 생성 완료"
        );


        /*
         * ========================================================
         * 4단계
         * 이상 지출 분석
         * ========================================================
         */

        log.info(
                "=================================================="
        );

        log.info(
                "[4단계] 이상 지출 분석 시작"
        );


        abnormalService.analyzeNewTransactions(
                transactions
        );


        log.info(
                "[4단계] 이상 지출 분석 완료"
        );


        /*
         * ========================================================
         * 5단계
         * 정기결제 분석
         * ========================================================
         */

        log.info(
                "=================================================="
        );

        log.info(
                "[5단계] 정기결제 분석 시작"
        );


        settingsService.getCandidates(
                member
        );


        log.info(
                "[5단계] 정기결제 분석 완료"
        );


        /*
         * ========================================================
         * 전체 완료
         * ========================================================
         */

        log.info(
                "=================================================="
        );

        log.info(
                "거래내역 전체 처리 완료"
        );

        log.info(
                "memberId={}, transactionCount={}",
                memberId,
                transactionIds.size()
        );

        log.info(
                "=================================================="
        );


        return transactionIds;
    }
}
