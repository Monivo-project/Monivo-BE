package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Subscription;
import com.example.monivobe.domain.transaction.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    /**
     * 회원의 특정 상태 정기결제 조회
     */
    List<Subscription> findByMemberAndStatus(
            Member member,
            Status status
    );

    Optional<Subscription> findByIdAndMember(
            Long id,
            Member member
    );

    boolean existsByMemberAndMerchantAndStatus(
            Member member,
            String merchant,
            Status status
    );

    Optional<Subscription> findByMemberAndMerchant(Member member, String merchant);
}
