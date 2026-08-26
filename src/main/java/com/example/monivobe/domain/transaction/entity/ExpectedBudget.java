package com.example.monivobe.domain.transaction.entity;

import com.example.monivobe.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 예상 지출을 계산한 연도
    @Column(nullable = false)
    private Integer targetYear;

    // 예상 지출을 계산한 월
    @Column(nullable = false)
    private Integer targetMonth;

    // AI가 예측한 이번 달 예상 지출
    @Column(nullable = false)
    private Integer expectedAmount;

    // 권장 예산
    @Column(nullable = false)
    private Integer recommendedBudget;

    // 현재까지 사용한 금액
    @Column(nullable = false)
    private Integer currentAmount;

    // 남은 예상 지출
    @Column(nullable = false)
    private Integer remainingExpectedAmount;

    // AI 예측 근거
    @Column(columnDefinition = "TEXT")
    private String reason;

    // 신뢰도
    private Integer confidence;

    // 분석한 개월 수
    private Integer analyzedMonths;

    private LocalDateTime createdAt;
}