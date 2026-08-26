package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.AbnormalTransaction;
import com.example.monivobe.domain.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AbnormalTransactionRepository extends JpaRepository<AbnormalTransaction, Long> {
    Optional<AbnormalTransaction> findByMemberAndTransaction(
            Member member,
            Transaction transaction
    );
}
