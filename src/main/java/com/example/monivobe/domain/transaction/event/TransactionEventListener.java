package com.example.monivobe.domain.transaction.event;

import com.example.monivobe.domain.transaction.service.TransactionProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransactionProcessingService transactionProcessingService;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleTransactionUploaded(
            TransactionUploadedEvent event
    ) {

        transactionProcessingService.process(
                event.memberId(),
                event.fileKey()
        );
    }
}