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

            transactionProcessingService.process(
                    event.memberId(),
                    event.fileKey()
            );

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