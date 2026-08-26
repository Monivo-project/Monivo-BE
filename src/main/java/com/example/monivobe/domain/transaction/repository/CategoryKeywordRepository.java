package com.example.monivobe.domain.transaction.repository;

import aj.org.objectweb.asm.commons.Remapper;
import com.example.monivobe.domain.transaction.entity.CategoryKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryKeywordRepository extends JpaRepository<CategoryKeyword, Long> {
    List<CategoryKeyword> findAll();

    Optional<CategoryKeyword>
    findFirstByKeywordIgnoreCase(
            String keyword
    );
}
