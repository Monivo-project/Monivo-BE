package com.example.monivobe.domain.transaction.event;

import com.example.monivobe.domain.transaction.service.TransactionProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionUploadedEventListener {

    private final TransactionProcessingService transactionProcessingService;


    /**
     * ============================================================
     * 거래내역 파일 업로드 후 처리
     * ============================================================
     *
     * 파일 업로드를 발생시킨 원래 트랜잭션이
     * COMMIT된 이후 비동기로 실행된다.
     *
     * AFTER_COMMIT
     *      ↓
     * @Async
     *      ↓
     * TransactionProcessingService
     *
     * 내부에서 각각의 작업이 별도 트랜잭션으로 실행된다.
     */
    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleTransactionUploaded(
            TransactionUploadedEvent event
    ) {

        log.info(
                "========================================"
        );

        log.info(
                "[TRANSACTION PROCESSING] 거래내역 파일 처리 시작"
        );

        log.info(
                "[TRANSACTION PROCESSING] memberId={}, fileKey={}",
                event.memberId(),
                event.fileKey()
        );

        log.info(
                "========================================"
        );


        try {

            /*
             * ====================================================
             * 거래내역 전체 처리
             *
             * TransactionProcessingService 내부에서
             *
             * [트랜잭션 1]
             * Excel 파싱
             * + Transaction 저장
             * ↓
             * COMMIT
             *
             * [트랜잭션 2]
             * LLM 분류
             * + Naver
             * + Kakao
             * + Merchant 저장
             * ↓
             * COMMIT
             *
             * [트랜잭션 3]
             * 예상 지출
             * ↓
             * COMMIT
             *
             * [트랜잭션 4]
             * 이상 지출
             * ↓
             * COMMIT
             *
             * [트랜잭션 5]
             * 정기결제 후보
             * ↓
             * COMMIT
             *
             * 구조로 실행한다.
             * ====================================================
             */

            transactionProcessingService.process(
                    event.memberId(),
                    event.fileKey()
            );


            log.info(
                    "========================================"
            );

            log.info(
                    "[TRANSACTION PROCESSING] 거래내역 파일 처리 완료"
            );

            log.info(
                    "[TRANSACTION PROCESSING] memberId={}, fileKey={}",
                    event.memberId(),
                    event.fileKey()
            );

            log.info(
                    "========================================"
            );


        } catch (Exception e) {

            /*
             * ====================================================
             * 비동기 처리이기 때문에
             *
             * 여기서 예외를 로그로 남긴다.
             *
             * 파일 업로드 자체는 이미 AFTER_COMMIT 상태이므로
             * 여기서 예외가 발생해도 업로드 트랜잭션을
             * rollback할 수 없다.
             * ====================================================
             */

            log.error(
                    "[TRANSACTION PROCESSING] 거래내역 파일 처리 실패 - memberId={}, fileKey={}",
                    event.memberId(),
                    event.fileKey(),
                    e
            );
        }
    }
}