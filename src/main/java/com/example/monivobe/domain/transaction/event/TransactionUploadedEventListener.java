package com.example.monivobe.domain.transaction.event;

import com.example.monivobe.domain.transaction.service.TransactionProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TransactionUploadedEventListener {

    private final TransactionProcessingService transactionProcessingService;

    @Async("transactionTaskExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(TransactionUploadedEvent event) {

        transactionProcessingService.process(
                event.memberId(),
                event.fileKey()
        );
    }
}