package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.dto.response.AiResponse;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.repository.CategoryRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionAiService {

    private final TransactionRepository transactionRepository;

    private final CategoryRepository categoryRepository;

    private final ChatClient chatClient;

    private final TransactionPromptService
            transactionPromptService;

    private final TransactionOntologyService
            transactionOntologyService;


    /**
     * ============================================================
     * LLM 미분류 거래 분류
     * ============================================================
     *
     * 1. UNCONFIRMED 거래만 LLM에게 전달
     * 2. LLM 분류 결과 및 confidence 확인
     *
     * confidence >= 0.7
     *      → 실제 category에 저장
     *      → ClassificationType = LLM
     *
     * confidence < 0.7
     *      → candidateCategory에 저장
     *      → 실제 category는 NULL
     *      → ClassificationType = UNCONFIRMED
     *      → 사용자에게 추천 카테고리로 제공
     *
     * ============================================================
     */
    @Transactional
    public void classifyUnclassifiedTransactions(
            List<Transaction> transactions
    ) {

        if (
                transactions == null
                        || transactions.isEmpty()
        ) {
            return;
        }


        /*
         * ========================================================
         * 미확정 거래만 선택
         * ========================================================
         */
        List<Transaction> unclassifiedTransactions =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getClassificationType()
                                        == ClassificationType.UNCONFIRMED
                        )
                        .toList();


        if (unclassifiedTransactions.isEmpty()) {
            return;
        }


        /*
         * ========================================================
         * LLM 분류
         * ========================================================
         */
        List<AiResponse.TransactionClassification> results =
                classifyByLlm(
                        unclassifiedTransactions
                );


        if (results == null || results.isEmpty()) {
            return;
        }


        /*
         * ========================================================
         * LLM 결과 반영
         * ========================================================
         */
        for (
                AiResponse.TransactionClassification result
                : results
        ) {

            /*
             * ----------------------------------------------------
             * 거래 조회
             * ----------------------------------------------------
             */
            Transaction transaction =
                    transactionRepository
                            .findById(
                                    result.transactionId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "거래내역을 찾을 수 없습니다. id="
                                                    + result.transactionId()
                                    )
                            );


            /*
             * ----------------------------------------------------
             * confidence 저장
             * ----------------------------------------------------
             */
            transaction.setConfidence(
                    result.confidence()
            );


            /*
             * ----------------------------------------------------
             * LLM이 추천한 Category 조회
             *
             * categoryId가 없거나
             * 존재하지 않는 category라면 null
             * ----------------------------------------------------
             */
            Category category = null;

            if (result.categoryId() != null) {

                category =
                        categoryRepository
                                .findById(
                                        result.categoryId()
                                )
                                .orElse(null);
            }


            /*
             * ====================================================
             * 신뢰도가 낮은 경우
             * ====================================================
             *
             * confidence < 0.7
             *
             * 또는
             *
             * confidence == null
             *
             * 또는
             *
             * category가 존재하지 않는 경우
             *
             * → 실제 category에는 저장하지 않는다.
             * → candidateCategory에 추천 카테고리를 저장한다.
             * ====================================================
             */
            if (
                    result.confidence() == null
                            || result.confidence() < 0.7
                            || category == null
            ) {

                /*
                 * LLM 추천 카테고리
                 */
                transaction.setCandidateCategory(
                        category
                );


                /*
                 * 실제 확정 카테고리는 제거
                 */
                transaction.setCategory(
                        null
                );


                /*
                 * 아직 확정되지 않은 거래
                 */
                transaction.setClassificationType(
                        ClassificationType.UNCONFIRMED
                );


                /*
                 * DB 저장
                 */
                transactionRepository.save(
                        transaction
                );


                /*
                 * Ontology 업데이트
                 */
                transactionOntologyService
                        .updateClassification(
                                transaction
                        );

                continue;
            }


            /*
             * ====================================================
             * 신뢰도가 충분한 경우
             * ====================================================
             *
             * confidence >= 0.7
             *
             * → 실제 Category로 확정
             * → ClassificationType = LLM
             * ====================================================
             */

            transaction.setCategory(
                    category
            );


            /*
             * 기존 후보 카테고리가 있었다면 제거
             */
            transaction.setCandidateCategory(
                    null
            );


            /*
             * LLM 분류 완료
             */
            transaction.setClassificationType(
                    ClassificationType.LLM
            );


            /*
             * DB 저장
             */
            transactionRepository.save(
                    transaction
            );


            /*
             * Ontology 업데이트
             */
            transactionOntologyService
                    .updateClassification(
                            transaction
                    );
        }
    }


    /**
     * ============================================================
     * LLM 호출
     * ============================================================
     */
    private List<AiResponse.TransactionClassification>
    classifyByLlm(
            List<Transaction> transactions
    ) {

        if (
                transactions == null
                        || transactions.isEmpty()
        ) {
            return List.of();
        }


        /*
         * ========================================================
         * 전체 카테고리 조회
         * ========================================================
         */
        List<Category> categories =
                categoryRepository.findAll();


        /*
         * ========================================================
         * Prompt 생성
         *
         * Merchant 정보와 거래 정보를 함께 전달
         * ========================================================
         */
        String prompt =
                transactionPromptService
                        .createClassificationPrompt(
                                categories,
                                transactions
                        );


        /*
         * ========================================================
         * LLM 호출
         * ========================================================
         */
        List<AiResponse.TransactionClassification> result =
                chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .entity(
                                new ParameterizedTypeReference<
                                        List<AiResponse.TransactionClassification>
                                        >() {
                                }
                        );


        return result != null
                ? result
                : List.of();
    }
}