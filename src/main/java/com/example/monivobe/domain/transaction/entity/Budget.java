package com.example.monivobe.domain.transaction.entity;

import com.example.monivobe.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;

@Entity
@Getter
@NoArgsConstructor
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private Integer year;

    private Integer month;

    private Integer amount;

    public Budget(
            Member member,
            Integer year,
            Integer month,
            Integer amount
    ) {
        this.member = member;
        this.year = year;
        this.month = month;
        this.amount = amount;
    }
}
