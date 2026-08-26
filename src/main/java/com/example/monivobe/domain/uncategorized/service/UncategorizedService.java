package com.example.monivobe.domain.uncategorized.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import com.example.monivobe.domain.uncategorized.dto.UncategorizedResDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UncategorizedService {

    private final TransactionRepository transactionRepository;

    /**
     * 미분류 거래 목록 조회
     *
     * 조건:
     * 1. 해당 회원의 거래
     * 2. ClassificationType = UNCONFIRMED
     * 3. LLM이 추천한 candidateCategory가 존재하는 거래
     */
    public List<UncategorizedResDTO.GetUncategorized> getUncategorized(
            Member member
    ) {

        List<Transaction> transactions =
                transactionRepository
                        .findByMemberAndClassificationType(
                                member,
                                ClassificationType.UNCONFIRMED
                        );

        return transactions.stream()
                .filter(transaction ->
                        transaction.getCandidateCategory() != null
                )
                .map(transaction ->
                        UncategorizedResDTO.GetUncategorized.builder()
                                .transactionId(transaction.getId())
                                .merchant(transaction.getMerchant())
                                .candidateCategoryId(
                                        transaction.getCandidateCategory().getId()
                                )
                                .candidateCategoryName(
                                        transaction.getCandidateCategory().getName()
                                )
                                .date(transaction.getDate().toLocalDate())
                                .amount(transaction.getAmount())
                                .confidence(transaction.getConfidence())
                                .build()
                )
                .toList();
    }
}
