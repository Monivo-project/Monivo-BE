package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.CategoryKeyword;
import com.example.monivobe.domain.transaction.entity.MemberCategoryKeyword;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.repository.CategoryKeywordRepository;
import com.example.monivobe.domain.transaction.repository.MemberCategoryKeywordRepository;
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

    private final MemberCategoryKeywordRepository
            memberCategoryKeywordRepository;

    private final CategoryKeywordRepository
            categoryKeywordRepository;


    /**
     * ============================================================
     * 거래 분류
     * ============================================================
     *
     * 1차 : MemberCategoryKeyword
     * 2차 : CategoryKeyword
     * 3차 : AI
     *
     * ============================================================
     */
    @Transactional
    public void classify(
            Long memberId,
            List<Long> transactionIds
    ) {

        if (
                transactionIds == null
                        || transactionIds.isEmpty()
        ) {
            return;
        }


        /*
         * ========================================================
         * 거래 조회
         * ========================================================
         */
        List<Transaction> transactions =
                transactionRepository.findAllById(
                        transactionIds
                );


        if (transactions.isEmpty()) {
            return;
        }


        /*
         * ========================================================
         * 회원 확인
         * ========================================================
         */
        Member member =
                transactions.get(0).getMember();


        if (member == null) {
            return;
        }


        /*
         * ========================================================
         * 1차 + 2차 키워드 분류
         * ========================================================
         */
        for (Transaction transaction : transactions) {

            if (
                    transaction.getClassificationType()
                            != ClassificationType.UNCONFIRMED
            ) {
                continue;
            }


            /*
             * ====================================================
             * 1차
             * MemberCategoryKeyword
             * ====================================================
             */
            Category category =
                    findMemberCategory(
                            member,
                            transaction.getMerchant()
                    );


            if (category != null) {

                transaction.setCategory(
                        category
                );

                transaction.setCandidateCategory(
                        null
                );

                transaction.setConfidence(
                        null
                );

                transaction.setClassificationType(
                        ClassificationType.USER
                );

                continue;
            }


            /*
             * ====================================================
             * 2차
             * CategoryKeyword
             * ====================================================
             */
            category =
                    findCategoryKeyword(
                            transaction.getMerchant()
                    );


            if (category != null) {

                transaction.setCategory(
                        category
                );

                transaction.setCandidateCategory(
                        null
                );

                transaction.setConfidence(
                        null
                );

                transaction.setClassificationType(
                        ClassificationType.KEYWORD
                );
            }
        }


        /*
         * ========================================================
         * DB 반영
         * ========================================================
         */
        transactionRepository.saveAll(
                transactions
        );


        /*
         * ========================================================
         * 3차 AI
         *
         * 아직 UNCONFIRMED인 거래만
         * ========================================================
         */
        List<Transaction> aiTargets =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getClassificationType()
                                        == ClassificationType.UNCONFIRMED
                        )
                        .toList();


        if (!aiTargets.isEmpty()) {

            transactionAiService
                    .classifyUnclassifiedTransactions(
                            aiTargets
                    );
        }


        /*
         * ========================================================
         * Merchant 처리
         * ========================================================
         */
        for (Transaction transaction : transactions) {

            processMerchant(
                    transaction
            );
        }
    }


    /**
     * ============================================================
     * 기존 메서드
     * ============================================================
     */
    @Transactional
    public void classify(
            List<Long> transactionIds
    ) {

        if (
                transactionIds == null
                        || transactionIds.isEmpty()
        ) {
            return;
        }


        List<Transaction> transactions =
                transactionRepository.findAllById(
                        transactionIds
                );


        if (transactions.isEmpty()) {
            return;
        }


        Member member =
                transactions.get(0).getMember();


        if (member == null) {
            return;
        }


        classify(
                member.getId(),
                transactionIds
        );
    }


    /**
     * ============================================================
     * 1차
     * MemberCategoryKeyword 검색
     * ============================================================
     */
    private Category findMemberCategory(
            Member member,
            String merchant
    ) {

        if (
                merchant == null
                        || merchant.isBlank()
        ) {
            return null;
        }


        /*
         * 거래처명을 정규화해서 검색하고 싶다면
         * 여기서 normalize 로직을 추가하면 된다.
         */
        return memberCategoryKeywordRepository
                .findFirstByMemberAndKeywordIgnoreCase(
                        member,
                        merchant
                )
                .map(
                        MemberCategoryKeyword::getCategory
                )
                .orElse(null);
    }


    /**
     * ============================================================
     * 2차
     * CategoryKeyword 검색
     * ============================================================
     */
    private Category findCategoryKeyword(
            String merchant
    ) {

        if (
                merchant == null
                        || merchant.isBlank()
        ) {
            return null;
        }


        return categoryKeywordRepository
                .findFirstByKeywordIgnoreCase(
                        merchant
                )
                .map(
                        CategoryKeyword::getCategory
                )
                .orElse(null);
    }


    /**
     * ============================================================
     * Merchant 정보 처리
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


        if (
                transaction.getMerchantInfo() != null
        ) {
            return;
        }


        merchantService
                .findOrCreateMerchant(
                        merchant
                )
                .ifPresent(
                        transaction::setMerchantInfo
                );
    }
}