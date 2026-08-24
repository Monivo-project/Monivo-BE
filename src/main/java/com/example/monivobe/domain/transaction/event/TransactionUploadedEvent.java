package com.example.monivobe.domain.transaction.event;

public record TransactionUploadedEvent(
        Long memberId,
        String fileKey
) {
}