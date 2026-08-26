package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.abnormal.service.AbnormalService;
import com.example.monivobe.domain.home.service.ExpectedBudgetService;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.domain.settings.service.SettingsService;
import com.example.monivobe.domain.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
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
     *
     * 파일 업로드
     *      ↓
     * [트랜잭션 1]
     * Excel 파싱 + 거래내역 저장
     *      ↓
     * COMMIT
     *
     *      ↓
     *
     * [트랜잭션 2]
     * Merchant 처리 + LLM 분류
     *      ↓
     * COMMIT
     *
     *      ↓
     *
     * [트랜잭션 3]
     * 예상 지출 생성
     *      ↓
     * COMMIT
     *
     *      ↓
     *
     * [트랜잭션 4]
     * 이상 지출 분석
     *      ↓
     * COMMIT
     *
     *      ↓
     *
     * [트랜잭션 5]
     * 정기결제 분석
     *      ↓
     * PENDING Subscription 생성
     *      ↓
     * COMMIT
     * ============================================================
     */
    public List<Long> process(
            Long memberId,
            String fileKey
    ) {

        /*
         * ========================================================
         * 1단계
         *
         * Excel 파싱
         *      ↓
         * Transaction 생성
         *      ↓
         * Keyword 분류
         *      ↓
         * DB 저장
         *      ↓
         * COMMIT
         * ========================================================
         */

        List<Transaction> transactions =
                transactionImportService.importTransactions(
                        memberId,
                        fileKey
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


        /*
         * ========================================================
         * 2단계
         *
         * Merchant 처리
         * +
         * LLM 분류
         *
         * TransactionClassificationService 내부의
         * @Transactional에서 처리
         * ========================================================
         */

        transactionClassificationService.classify(
                transactionIds
        );


        /*
         * ========================================================
         * Member 조회
         * ========================================================
         */

        Member member =
                memberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "회원을 찾을 수 없습니다. memberId="
                                                + memberId
                                )
                        );


        /*
         * ========================================================
         * 3단계
         *
         * 예상 지출 생성
         *
         * 현재 월의 예상 지출이 없을 경우에만 생성
         * ========================================================
         */

        expectedBudgetService.createExpectedBudgetIfNotExists(
                member,
                YearMonth.now()
        );


        /*
         * ========================================================
         * 4단계
         *
         * 이상 지출 분석
         *
         * 새롭게 업로드된 거래만 분석
         *
         * 이상 여부 판단을 위해
         * AbnormalService 내부에서 기존 거래도 조회
         * ========================================================
         */

        abnormalService.analyzeNewTransactions(
                transactions
        );


        /*
         * ========================================================
         * 5단계
         *
         * 정기결제 분석
         *
         * SettingsService의 getCandidates()에서
         *
         * 1. 전체 거래 조회
         * 2. 가맹점별 거래 그룹화
         * 3. 2회 이상 거래 확인
         * 4. 결제 간격 계산
         * 5. MONTHLY / YEARLY 판단
         * 6. PENDING Subscription 생성
         * 7. SubscriptionTransaction 생성
         *
         * 까지 처리한다.
         *
         * SettingsService.getCandidates()는
         * @Transactional이므로 별도의 트랜잭션으로 실행된다.
         * ========================================================
         */

        settingsService.getCandidates(
                member
        );


        /*
         * ========================================================
         * 모든 자동 처리 완료
         *
         * 1. 거래내역 저장
         * 2. Merchant + LLM 분류
         * 3. 예상 지출
         * 4. 이상 지출
         * 5. 정기결제 후보
         * ========================================================
         */

        return transactionIds;
    }
}