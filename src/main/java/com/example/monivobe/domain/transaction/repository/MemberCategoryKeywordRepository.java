package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.MemberCategoryKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberCategoryKeywordRepository  extends JpaRepository<MemberCategoryKeyword, Long> {
    Optional<MemberCategoryKeyword> findByMemberAndKeyword(
            Member member,
            String keyword
    );
}
