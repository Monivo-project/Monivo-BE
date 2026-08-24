package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.CategoryBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryBudgetRepository  extends JpaRepository<CategoryBudget, Long> {

    List<CategoryBudget> findByMemberAndYearAndMonth(
            Member member,
            Integer year,
            Integer month
    );
}
