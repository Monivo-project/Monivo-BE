package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.transaction.entity.CategoryKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryKeywordRepository extends JpaRepository<CategoryKeyword, Long> {
    List<CategoryKeyword> findAll();
}
