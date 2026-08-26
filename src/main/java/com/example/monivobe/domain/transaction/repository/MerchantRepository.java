package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.transaction.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository
        extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByNormalizedName(
            String normalizedName
    );
}
