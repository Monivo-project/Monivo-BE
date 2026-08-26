package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.Transaction;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionPromptService {

    public String createClassificationPrompt(
            List<Category> categories,
            List<Transaction> transactions
    ) {

        String categoryInfo = categories.stream()
                .map(category ->
                        category.getId() + ": "
                                + category.getName()
                                + " - "
                                + category.getDescription()
                )
                .collect(Collectors.joining("\n"));

        String transactionInfo = transactions.stream()
                .map(transaction ->
                        """
                        transactionId: %d
                        merchant: %s
                        amount: %d
                        date: %s
                        """.formatted(
                                transaction.getId(),
                                transaction.getMerchant(),
                                transaction.getAmount(),
                                transaction.getDate()
                        )
                )
                .collect(Collectors.joining("\n"));

        return """
                당신은 사용자의 소비내역을 카테고리로 분류하는 소비 분석 AI입니다.

                아래 카테고리 중 반드시 하나를 선택해야 합니다.

                [카테고리]
                %s

                [거래내역]
                %s

                다음 기준을 반드시 지켜주세요.

                1. 거래처명, 금액, 거래 내용을 종합적으로 판단하세요.
                2. 반드시 위 카테고리 중 하나의 categoryId를 선택하세요.
                3. confidence는 0.0 ~ 1.0 사이의 값으로 작성하세요.
                4. confidence가 0.7 미만이면 categoryId를 null로 반환하세요.
                5. 거래처만으로 판단하기 어려운 경우 억지로 분류하지 마세요.
                6. 애매한 거래는 사용자가 직접 확인할 수 있도록 해야 합니다.

                반드시 JSON 형식으로 반환하세요.
                """.formatted(
                categoryInfo,
                transactionInfo
        );
    }
}