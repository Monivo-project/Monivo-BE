package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.transaction.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
