package com.example.monivobe.domain.transaction.entity;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private String merchant;

    private String normalizedMerchant;

    private Integer amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ClassificationType classificationType;

    private LocalDateTime date;

    private Double confidence;

    private Boolean isAbnormal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchantInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_category_id")
    private Category candidateCategory;

    public Transaction(
            Member member,
            String merchant,
            Integer amount,
            LocalDateTime date,
            TransactionType transactionType
    ) {
        this.member = member;
        this.merchant = merchant;
        this.amount = amount;
        this.date = date;
        this.transactionType = transactionType;

        this.classificationType =
                ClassificationType.UNCLASSIFIED;

        this.isAbnormal = false;
        this.confidence = null;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}