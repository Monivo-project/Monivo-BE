package com.example.monivobe.domain.transaction.event;

import com.example.monivobe.domain.abnormal.service.AbnormalService;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.service.TransactionProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionUploadedEventListener {

    private final TransactionProcessingService transactionProcessingService;
    private final AbnormalService abnormalService;

    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleTransactionUploaded(
            TransactionUploadedEvent event
    ) {

        log.info(
                "거래내역 파일 처리 시작 - memberId={}, fileKey={}",
                event.memberId(),
                event.fileKey()
        );

        try {

            /*
             * 이번 파일에서 새롭게 생성된 거래만 반환
             */
            List<Transaction> newTransactions =
                    transactionProcessingService.process(
                            event.memberId(),
                            event.fileKey()
                    );


            /*
             * 새로운 거래가 있을 때만
             * 이상 지출 분석
             */
            if (!newTransactions.isEmpty()) {

                abnormalService.analyzeNewTransactions(
                        newTransactions
                );

                log.info(
                        "새로운 거래 {}건 이상 지출 분석 완료",
                        newTransactions.size()
                );

            } else {

                log.info(
                        "새로운 거래가 없어 이상 지출 분석을 수행하지 않습니다."
                );
            }


            log.info(
                    "거래내역 파일 처리 완료 - memberId={}, fileKey={}",
                    event.memberId(),
                    event.fileKey()
            );

        } catch (Exception e) {

            log.error(
                    "거래내역 파일 처리 실패 - memberId={}, fileKey={}",
                    event.memberId(),
                    event.fileKey(),
                    e
            );
        }
    }
}