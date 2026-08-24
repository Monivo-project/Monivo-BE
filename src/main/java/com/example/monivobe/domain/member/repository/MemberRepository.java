package com.example.monivobe.domain.member.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.enums.SocialType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findBySocialTypeAndSocialUid(SocialType providerId, String socialUid);

    boolean existsByName(@NotBlank String nickname);
}
