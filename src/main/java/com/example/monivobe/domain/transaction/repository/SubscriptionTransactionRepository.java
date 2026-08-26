package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.transaction.entity.Subscription;
import com.example.monivobe.domain.transaction.entity.SubscriptionTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionTransactionRepository
        extends JpaRepository<SubscriptionTransaction, Long> {

    List<SubscriptionTransaction> findBySubscription(
            Subscription subscription
    );

}
