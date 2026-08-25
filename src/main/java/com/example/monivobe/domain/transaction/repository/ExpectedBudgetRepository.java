package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.ExpectedBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpectedBudgetRepository
        extends JpaRepository<ExpectedBudget, Long> {

    Optional<ExpectedBudget> findByMemberAndTargetYearAndTargetMonth(
            Member member,
            Integer targetYear,
            Integer targetMonth
    );
}