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
    private final TransactionPromptService transactionPromptService;

    @Transactional
    public void classifyUnclassifiedTransactions(
            List<Transaction> transactions
    ) {

        if (transactions == null || transactions.isEmpty()) {
            return;
        }

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

        List<AiResponse.TransactionClassification> results =
                classifyByLlm(unclassifiedTransactions);

        for (AiResponse.TransactionClassification result : results) {

            Transaction transaction =
                    transactionRepository.findById(
                            result.transactionId()
                    ).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "거래내역을 찾을 수 없습니다. id="
                                            + result.transactionId()
                            )
                    );

            // 신뢰도가 낮거나 카테고리가 없는 경우
            if (result.confidence() == null
                    || result.confidence() < 0.7
                    || result.categoryId() == null) {

                transaction.setClassificationType(
                        ClassificationType.UNCONFIRMED
                );

                continue;
            }

            Category category =
                    categoryRepository.findById(
                            result.categoryId()
                    ).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "카테고리를 찾을 수 없습니다. id="
                                            + result.categoryId()
                            )
                    );

            transaction.setCategory(category);

            transaction.setClassificationType(
                    ClassificationType.LLM
            );
        }
    }

    private List<AiResponse.TransactionClassification> classifyByLlm(
            List<Transaction> transactions
    ) {

        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }

        List<Category> categories =
                categoryRepository.findAll();

        String prompt =
                transactionPromptService.createClassificationPrompt(
                        categories,
                        transactions
                );

        List<AiResponse.TransactionClassification> result =
                chatClient.prompt()
                        .user(prompt)
                        .call()
                        .entity(
                                new ParameterizedTypeReference<
                                        List<AiResponse.TransactionClassification>
                                        >() {
                                }
                        );

        return result != null ? result : List.of();
    }
}