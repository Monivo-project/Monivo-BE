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
         * ============================================================
         * 카테고리 정보
         * ============================================================
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
         * ============================================================
         * 거래내역
         * ============================================================
         */

        String transactionInfo =
                transactions.stream()

                        .map(transaction -> {

                            Merchant merchantInfo =
                                    transaction.getMerchantInfo();

                            String externalMerchantInfo;


                            /*
                             * ------------------------------------------------
                             * 외부 가맹점 정보가 있는 경우
                             * ------------------------------------------------
                             */

                            if (merchantInfo != null) {

                                externalMerchantInfo =
                                        """
                                        가맹점명: %s
                                        대분류 업종: %s
                                        상세 업종: %s
                                        주소: %s
                                        도로명주소: %s
                                        """.formatted(

                                                merchantInfo.getName(),

                                                merchantInfo.getBusinessType(),

                                                merchantInfo.getBusinessCategory(),

                                                merchantInfo.getAddress(),

                                                merchantInfo.getRoadAddress()
                                        );

                            }

                            /*
                             * ------------------------------------------------
                             * 외부 가맹점 정보가 없는 경우
                             * ------------------------------------------------
                             */

                            else {

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

                                    transaction.getMerchant(),

                                    transaction.getAmount(),

                                    transaction.getDate(),

                                    externalMerchantInfo
                            );
                        })

                        .collect(
                                Collectors.joining("\n")
                        );


        /*
         * ============================================================
         * Prompt
         * ============================================================
         */

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
                   중요한 판단 근거로 사용하세요.


                3. 다음 정보를 종합적으로 판단하세요.

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


                5. 반드시 위에 제공된 카테고리 중
                   거래에 가장 적합한 카테고리를 하나 선택하세요.


                6. confidence는
                   0.0 ~ 1.0 사이의 값으로 작성하세요.


                7. 매우 중요한 규칙:

                   confidence가 0.7 미만이더라도
                   categoryId를 null로 반환하지 마세요.

                   confidence가 낮은 경우에도
                   AI가 가장 적합하다고 판단한 후보 카테고리의
                   categoryId를 반드시 반환하세요.

                   즉,

                   confidence >= 0.7
                   → 해당 categoryId를 실제 분류에 사용할 수 있음

                   confidence < 0.7
                   → 해당 categoryId는 후보 카테고리로 사용됨


                8. confidence가 0.7 미만인 경우에도
                   가장 가능성이 높은 카테고리를 선택하세요.

                   예를 들어:

                   {
                     "transactionId": 123,
                     "categoryId": 3,
                     "confidence": 0.55
                   }

                   이런 형태로 반환해야 합니다.

                   categoryId = 3은 후보 카테고리로 저장됩니다.


                9. 외부 가맹점 정보가 없고
                   거래처명만으로 판단하기 어려운 경우에도
                   가장 가능성이 높은 카테고리를 선택하되
                   confidence를 낮게 설정하세요.


                10. 서로 다른 가맹점 검색 결과가 존재하거나
                    가맹점 정보가 불확실한 경우에도
                    가장 가능성이 높은 카테고리를 선택하고
                    confidence를 낮게 설정하세요.


                11. categoryId는 반드시
                    위에서 제공된 카테고리 ID 중 하나여야 합니다.


                12. 최종 결과는 반드시
                    JSON 배열 형식으로 반환하세요.


                13. 각 거래마다 반드시 다음 정보를 포함하세요.

                    - transactionId
                    - categoryId
                    - confidence


                14. categoryId를 임의로 만들지 마세요.
                    제공된 카테고리 목록에 존재하는 ID만 사용하세요.


                ==========================
                [출력 예시]
                ==========================

                [
                  {
                    "transactionId": 101,
                    "categoryId": 1,
                    "confidence": 0.92
                  },
                  {
                    "transactionId": 102,
                    "categoryId": 5,
                    "confidence": 0.61
                  }
                ]


                위 예시에서 transactionId=102는
                confidence가 0.7 미만이므로
                실제 category가 아니라 candidateCategory로 저장됩니다.

                """.formatted(
                categoryInfo,
                transactionInfo
        );
    }
}