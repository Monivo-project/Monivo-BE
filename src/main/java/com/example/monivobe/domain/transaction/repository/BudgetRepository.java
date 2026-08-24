package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByMemberAndYearAndMonth(
            Member member,
            Integer year,
            Integer month
    );
}