package com.example.monivobe.domain.transaction.repository;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.enums.Status;
import com.example.monivobe.domain.transaction.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // 월별 거래 조회
    List<Transaction> findByMemberAndDateGreaterThanEqualAndDateLessThanOrderByDateDesc(
            Member member,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // 거래 상세 + 회원 검증
    Optional<Transaction> findByIdAndMember(
            Long id,
            Member member
    );

    Page<Transaction>
    findByMemberAndDateGreaterThanEqualAndDateLessThan(
            Member member,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    // 카테고리별 지출
    @Query("""
        SELECT t.category.id, 
               t.category.name, 
               SUM(t.amount)
        FROM Transaction t
        WHERE t.member = :member
          AND t.date >= :startDate
          AND t.date < :endDate
          AND t.category IS NOT NULL
        GROUP BY t.category.id, t.category.name
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> findCategorySummary(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // 거래 검색
    @Query("""
    SELECT t
    FROM Transaction t
    WHERE t.member = :member
      AND t.date >= :startDate
      AND t.date < :endDate

      AND (
          :merchant IS NULL
          OR :merchant = ''
          OR LOWER(t.merchant) LIKE LOWER(CONCAT('%', :merchant, '%'))
      )

      AND (
          :categoryId IS NULL
          OR t.category.id = :categoryId
      )

      AND (
          :classification IS NULL
          OR (
              :classification = 'CLASSIFIED'
              AND t.classificationType NOT IN (
                  com.example.monivobe.domain.transaction.enums.ClassificationType.UNCLASSIFIED,
                  com.example.monivobe.domain.transaction.enums.ClassificationType.UNCONFIRMED
              )
          )
          OR (
              :classification = 'UNCLASSIFIED'
              AND t.classificationType IN (
                  com.example.monivobe.domain.transaction.enums.ClassificationType.UNCLASSIFIED,
                  com.example.monivobe.domain.transaction.enums.ClassificationType.UNCONFIRMED
              )
          )
      )

    ORDER BY t.date DESC
""")
    Page<Transaction> searchTransactions(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchant") String merchant,
            @Param("categoryId") Long categoryId,
            @Param("classification") String classification,
            Pageable pageable
    );

    // 이상 지출 개수
    long countByMemberAndDateGreaterThanEqualAndDateLessThanAndIsAbnormalTrue(
            Member member,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // 미분류 개수
    long countByMemberAndDateGreaterThanEqualAndDateLessThanAndClassificationType(
            Member member,
            LocalDateTime startDate,
            LocalDateTime endDate,
            com.example.monivobe.domain.transaction.enums.ClassificationType classificationType
    );



    // home
    /**
     * 특정 기간의 총 지출
     */
    @Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM Transaction t
    WHERE t.member = :member
      AND t.date >= :startDate
      AND t.date < :endDate
      AND t.transactionType = com.example.monivobe.domain.transaction.enums.TransactionType.EXPENSE
""")
    Integer getTotalExpense(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    /**
     * 이상 지출 개수
     */
    @Query("""
        SELECT COUNT(t)
        FROM Transaction t
        WHERE t.member = :member
          AND t.date >= :startDate
          AND t.date < :endDate
          AND t.isAbnormal = true
    """)
    Integer countAbnormalTransactions(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    /**
     * 미분류 거래 개수
     */
    @Query("""
    SELECT COUNT(t)
    FROM Transaction t
    WHERE t.member = :member
      AND t.date >= :startDate
      AND t.date < :endDate
      AND t.classificationType = com.example.monivobe.domain.transaction.enums.ClassificationType.UNCLASSIFIED
      AND t.transactionType = com.example.monivobe.domain.transaction.enums.TransactionType.EXPENSE
""")
    Integer countUnclassifiedTransactions(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    /**
     * 특정 날짜의 지출
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.member = :member
          AND t.date >= :startDate
          AND t.date < :endDate
    """)
    Integer getDailyExpense(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    /**
     * 최근 거래 5건
     */
    List<Transaction> findTop3ByMemberOrderByDateDesc(
            Member member
    );







    // report
    /**
     * 특정 기간의 총 지출
     */
    @Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM Transaction t
    WHERE t.member = :member
      AND t.date >= :startDate
      AND t.date < :endDate
      AND t.transactionType = com.example.monivobe.domain.transaction.enums.TransactionType.EXPENSE
""")
    Integer getTotalAmount(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    /**
     * 카테고리별 지출
     */
    @Query("""
        SELECT
            c.id,
            c.name,
            COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        LEFT JOIN t.category c
        WHERE t.member = :member
          AND t.date >= :startDate
          AND t.date < :endDate
        GROUP BY c.id, c.name
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> getCategoryExpenses(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );



    // home
    List<Transaction> findByMemberAndDateBetweenOrderByDateAsc(
            Member member,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM Transaction t
    WHERE t.member = :member
      AND t.date >= :startDate
      AND t.date < :endDate
      AND t.transactionType = :transactionType
""")
    int sumAmountByMemberAndDateBetween(
            @Param("member") Member member,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("transactionType") TransactionType transactionType
    );




    // settings
    List<Transaction> findByMemberAndMerchantOrderByDateDesc(
            Member member,
            String merchant
    );

    List<Transaction> findByMemberOrderByDateDesc(Member member);

    List<Transaction> findAllByIdIn(List<Long> ids);


    // uncategorized
    List<Transaction> findByMemberAndClassificationType(
            Member member,
            ClassificationType classificationType
    );

    boolean existsByMemberAndMerchantAndAmountAndDate(
            Member member,
            String merchant,
            Integer amount,
            LocalDateTime date
    );

    List<Transaction> findByMemberAndNormalizedMerchant(
            Member member,
            String normalizedMerchant
    );

    List<Transaction> findByMemberAndMerchant(
            Member member,
            String merchant
    );
}
