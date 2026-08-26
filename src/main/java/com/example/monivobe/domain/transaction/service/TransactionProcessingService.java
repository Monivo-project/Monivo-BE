package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionProcessingService {

    private final TransactionImportService transactionImportService;

    private final TransactionClassificationService
            transactionClassificationService;


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
     *      ↓
     * [트랜잭션 2]
     * LLM 분류 + Merchant 처리
     *      ↓
     * COMMIT
     *
     * 이후
     *      ↓
     * [트랜잭션 3] 예상 지출
     *      ↓
     * [트랜잭션 4] 이상 지출
     *      ↓
     * [트랜잭션 5] 정기결제
     *
     * 로 확장한다.
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
         * DB 저장
         *      ↓
         * COMMIT
         *
         * TransactionImportService의
         * @Transactional에서 별도 트랜잭션으로 실행된다.
         * ========================================================
         */

        List<Transaction> transactions =
                transactionImportService.importTransactions(
                        memberId,
                        fileKey
                );


        /*
         * ========================================================
         * 새롭게 저장된 거래가 없는 경우
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
         * 저장된 Transaction ID 추출
         *
         * 다음 단계부터는 Entity 자체를 넘기지 않고
         * ID만 넘긴다.
         *
         * 이유:
         *
         * 트랜잭션 1이 이미 COMMIT된 상태이므로
         * 트랜잭션 2에서는 ID를 이용해서
         * 새로운 영속성 컨텍스트에서 다시 조회한다.
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
         * LLM 분류
         * +
         * 네이버 / 카카오 Merchant 처리
         *
         * TransactionClassificationService 내부의
         * @Transactional에서 별도 트랜잭션으로 실행된다.
         *
         * 정상 종료
         *      ↓
         * COMMIT
         * ========================================================
         */

        transactionClassificationService.classify(
                transactionIds
        );


        /*
         * ========================================================
         * 현재는 2단계까지만 처리
         *
         * 다음 단계에서
         *
         * transactionIds를 이용해서
         *
         * 3. 예상 지출
         * 4. 이상 지출
         * 5. 정기결제 후보
         *
         * 를 각각 별도의 트랜잭션으로 실행한다.
         * ========================================================
         */

        return transactionIds;
    }
}