package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
