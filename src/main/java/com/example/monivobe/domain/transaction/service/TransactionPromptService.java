package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.Merchant;
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

        /*
         * 카테고리 정보
         */
        String categoryInfo =
                categories.stream()

                        .map(category ->
                                category.getId()
                                        + ": "
                                        + category.getName()
                                        + " - "
                                        + category.getDescription()
                        )

                        .collect(
                                Collectors.joining("\n")
                        );


        /*
         * 거래내역
         */
        String transactionInfo =
                transactions.stream()

                        .map(transaction -> {

                            Merchant merchantInfo =
                                    transaction
                                            .getMerchantInfo();

                            String externalMerchantInfo;

                            if (
                                    merchantInfo
                                            != null
                            ) {

                                externalMerchantInfo =
                                        """
                                        가맹점명: %s
                                        대분류 업종: %s
                                        상세 업종: %s
                                        주소: %s
                                        도로명주소: %s
                                        """.formatted(

                                                merchantInfo
                                                        .getName(),

                                                merchantInfo
                                                        .getBusinessType(),

                                                merchantInfo
                                                        .getBusinessCategory(),

                                                merchantInfo
                                                        .getAddress(),

                                                merchantInfo
                                                        .getRoadAddress()
                                        );

                            } else {

                                externalMerchantInfo =
                                        "외부 가맹점 정보 없음";
                            }


                            return """
                                    transactionId: %d
                                    merchant: %s
                                    amount: %d
                                    date: %s

                                    [외부 가맹점 검색 정보]
                                    %s
                                    """.formatted(

                                    transaction.getId(),

                                    transaction
                                            .getMerchant(),

                                    transaction
                                            .getAmount(),

                                    transaction
                                            .getDate(),

                                    externalMerchantInfo
                            );
                        })

                        .collect(
                                Collectors.joining("\n")
                        );


        return """
                당신은 사용자의 소비내역을 분석하고
                소비 카테고리를 분류하는 AI입니다.

                아래 제공되는 카테고리 중
                거래에 가장 적합한 카테고리를 선택하세요.

                ==========================
                [카테고리]
                ==========================

                %s


                ==========================
                [거래내역]
                ==========================

                %s


                ==========================
                [분류 규칙]
                ==========================

                1. 거래처명만 보고 판단하지 마세요.

                2. 거래처명이 제공된 경우
                   외부 가맹점 검색 정보를
                   가장 중요한 근거 중 하나로 사용하세요.

                3. 특히 다음 정보를 종합적으로 판단하세요.

                   - 가맹점명
                   - 대분류 업종
                   - 상세 업종
                   - 거래금액
                   - 거래일시
                   - 주소


                4. 예를 들어:

                   merchant = "일락"

                   외부 정보:
                   업종 = "음식점"
                   상세 업종 = "음식점>한식"

                   이런 경우
                   "식비" 또는 음식 관련 카테고리를
                   우선적으로 선택해야 합니다.


                5. 반드시 위 카테고리 중
                   하나의 categoryId를 선택하세요.


                6. confidence는
                   0.0 ~ 1.0 사이의 값으로 작성하세요.


                7. confidence가 0.7 미만이면
                   categoryId를 null로 반환하세요.


                8. 외부 가맹점 정보가 없고
                   거래처명만으로 판단하기 어려운 경우
                   억지로 분류하지 마세요.


                9. 서로 다른 가맹점 검색 결과가 존재하거나
                   가맹점 정보가 불확실한 경우에도
                   억지로 분류하지 마세요.


                10. 최종 결과는 반드시
                    JSON 형식으로 반환하세요.
                """.formatted(
                categoryInfo,
                transactionInfo
        );
    }
}