package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionClassificationService {

    private final TransactionAiService transactionAiService;
    private final MerchantService merchantService;
    private final TransactionRepository transactionRepository;

    /**
     * ============================================================
     * 2단계
     * LLM 분류 + 가게 정보 처리
     * ============================================================
     *
     * [트랜잭션 2]
     *
     * 1. UNCONFIRMED 거래 LLM 분류
     * 2. 가게 정보 처리
     *
     * 완료 후 COMMIT
     */
    @Transactional
    public void classify(List<Long> transactionIds) {

        if (transactionIds == null || transactionIds.isEmpty()) {
            return;
        }

        // 트랜잭션 2에서 DB 재조회
        List<Transaction> transactions =
                transactionRepository.findAllById(transactionIds);

        if (transactions.isEmpty()) {
            return;
        }

        // UNCONFIRMED만 LLM 분류
        List<Transaction> unclassifiedTransactions =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getClassificationType()
                                        == ClassificationType.UNCONFIRMED
                        )
                        .toList();

        if (!unclassifiedTransactions.isEmpty()) {

            transactionAiService.classifyUnclassifiedTransactions(
                    unclassifiedTransactions
            );
        }

        // Merchant 처리
        for (Transaction transaction : transactions) {

            processMerchant(transaction);
        }
    }
    /**
     * ============================================================
     * 가게 정보 처리
     * ============================================================
     */
    private void processMerchant(
            Transaction transaction
    ) {

        String merchant =
                transaction.getMerchant();

        if (
                merchant == null
                        || merchant.isBlank()
        ) {
            return;
        }


        /*
         * ========================================================
         * 기존 Merchant가 이미 연결되어 있다면
         * 다시 검색하지 않는다.
         * ========================================================
         */
        if (transaction.getMerchantInfo() != null) {
            return;
        }


        /*
         * ========================================================
         * Merchant 조회 / 생성
         *
         * MerchantService 내부에서
         *
         * 네이버
         * +
         * 카카오
         *
         * API를 이용해서 가게 정보를 처리하도록 한다.
         * ========================================================
         */
        merchantService
                .findOrCreateMerchant(
                        merchant
                )
                .ifPresent(
                        transaction::setMerchantInfo
                );
    }
}