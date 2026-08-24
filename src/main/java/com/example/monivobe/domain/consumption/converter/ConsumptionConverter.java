package com.example.monivobe.domain.consumption.converter;

import com.example.monivobe.domain.consumption.dto.ConsumptionResDTO;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.Transaction;

public class ConsumptionConverter {

    public static ConsumptionResDTO.TransactionInfo toTransactionInfo(
            Transaction transaction
    ) {

        Category category = transaction.getCategory();

        return ConsumptionResDTO.TransactionInfo.builder()
                .transactionId(transaction.getId())
                .merchant(transaction.getMerchant())
                .amount(transaction.getAmount())
                .date(transaction.getDate())
                .categoryId(
                        category != null
                                ? category.getId()
                                : null
                )
                .categoryName(
                        category != null
                                ? category.getName()
                                : "미분류"
                )
                .classificationType(
                        transaction.getClassificationType()
                )
                .isAbnormal(
                        transaction.getIsAbnormal()
                )
                .build();
    }
}