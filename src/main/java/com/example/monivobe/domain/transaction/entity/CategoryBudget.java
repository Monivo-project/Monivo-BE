package com.example.monivobe.domain.transaction.entity;

import com.example.monivobe.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;

@Entity
@Getter
@NoArgsConstructor
public class CategoryBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private Integer year;

    private Integer month;

    private Integer amount;

    public CategoryBudget(
            Member member,
            Category category,
            Integer year,
            Integer month,
            Integer amount
    ) {
        this.member = member;
        this.category = category;
        this.year = year;
        this.month = month;
        this.amount = amount;
    }
}