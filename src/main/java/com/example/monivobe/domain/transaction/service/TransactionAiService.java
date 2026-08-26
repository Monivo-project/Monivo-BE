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
         * 미분류 거래만 선택
         */
        List<Transaction>
                unclassifiedTransactions =

                transactions.stream()

                        .filter(transaction ->
                                transaction
                                        .getClassificationType()
                                        == ClassificationType
                                        .UNCONFIRMED
                        )

                        .toList();

        if (
                unclassifiedTransactions
                        .isEmpty()
        ) {

            return;
        }


        /*
         * LLM 분류
         */
        List<AiResponse.TransactionClassification>
                results =

                classifyByLlm(
                        unclassifiedTransactions
                );


        /*
         * LLM 결과 반영
         */
        for (
                AiResponse.TransactionClassification result
                : results
        ) {

            Transaction transaction =
                    transactionRepository
                            .findById(
                                    result.transactionId()
                            )

                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "거래내역을 찾을 수 없습니다. id="
                                                    + result
                                                    .transactionId()
                                    )
                            );


            /*
             * 신뢰도 부족
             */
            if (
                    result.confidence() == null
                            || result.confidence() < 0.7
                            || result.categoryId() == null
            ) {

                transaction.setClassificationType(
                        ClassificationType.UNCONFIRMED
                );

                transactionOntologyService
                        .updateClassification(
                                transaction
                        );

                continue;
            }


            /*
             * Category 조회
             */
            Category category =
                    categoryRepository
                            .findById(
                                    result.categoryId()
                            )

                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "카테고리를 찾을 수 없습니다. id="
                                                    + result
                                                    .categoryId()
                                    )
                            );


            /*
             * Category 설정
             */
            transaction.setCategory(
                    category
            );

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
     * LLM 호출
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
         * 전체 카테고리 조회
         */
        List<Category> categories =
                categoryRepository.findAll();


        /*
         * Prompt 생성
         *
         * 여기서 Merchant 정보도
         * 함께 전달된다.
         */
        String prompt =
                transactionPromptService
                        .createClassificationPrompt(
                                categories,
                                transactions
                        );


        /*
         * LLM 호출
         */
        List<AiResponse.TransactionClassification>
                result =

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