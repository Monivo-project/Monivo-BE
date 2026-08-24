package com.example.monivobe.domain.home.converter;

import com.example.monivobe.domain.home.dto.HomeResDTO;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.Transaction;

public class HomeConverter {
    /**
     * Transaction → RecentTransaction
     */
    public static HomeResDTO.RecentTransaction
    toRecentTransaction(Transaction transaction) {

        Category category = transaction.getCategory();

        return HomeResDTO.RecentTransaction.builder()
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
