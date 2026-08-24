package com.example.monivobe.domain.transaction.entity;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
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

    private Boolean confidence;

    private Boolean isAbnormal;

    public Transaction(
            Member member,
            String merchant,
            Integer amount,
            LocalDateTime date
    ) {
        this.member = member;
        this.merchant = merchant;
        this.amount = amount;
        this.date = date;

        this.classificationType = ClassificationType.UNCLASSIFIED;
        this.isAbnormal = false;
        this.confidence = false;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
