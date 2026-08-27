package com.example.monivobe.domain.consumption.service;

import com.example.monivobe.domain.consumption.converter.ConsumptionConverter;
import com.example.monivobe.domain.consumption.dto.ConsumptionReqDTO;
import com.example.monivobe.domain.consumption.dto.ConsumptionResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.MemberCategoryKeyword;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.enums.TransactionType;
import com.example.monivobe.domain.transaction.repository.CategoryRepository;
import com.example.monivobe.domain.transaction.repository.MemberCategoryKeywordRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumptionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final MemberCategoryKeywordRepository memberCategoryKeywordRepository;


    /**
     * 월별 소비 내역 조회
     */
    public ConsumptionResDTO.GetConsumption getConsumption(
            Integer year,
            Integer month,
            int page,
            int size,
            Member member
    ) {

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        // =========================
        // Pageable
        // =========================

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));

        // =========================
        // 해당 월 거래 조회
        // =========================

        Page<Transaction> transactionPage =
                transactionRepository
                        .findByMemberAndDateGreaterThanEqualAndDateLessThan(
                                member,
                                startDate,
                                endDate,
                                pageable
                        );


        // =========================
        // 총 지출
        // =========================

        // 주의:
        // 현재 페이지의 거래가 아니라
        // 해당 월 전체 지출을 계산해야 함
        int totalAmount =
                transactionRepository
                        .sumAmountByMemberAndDateBetween(
                                member,
                                startDate,
                                endDate,
                                TransactionType.EXPENSE
                        );


        // =========================
        // 전체 거래 수
        // =========================

        long transactionCount =
                transactionPage.getTotalElements();


        // =========================
        // 이상 지출 수
        // =========================

        long abnormalCount =
                transactionRepository
                        .countByMemberAndDateGreaterThanEqualAndDateLessThanAndIsAbnormalTrue(
                                member,
                                startDate,
                                endDate
                        );


        // =========================
        // 미분류 수
        // =========================

        long unclassifiedCount =
                transactionRepository
                        .countByMemberAndDateGreaterThanEqualAndDateLessThanAndClassificationType(
                                member,
                                startDate,
                                endDate,
                                ClassificationType.UNCLASSIFIED
                        );


        // =========================
        // 현재 페이지 거래
        // =========================

        List<ConsumptionResDTO.TransactionInfo> transactionList =
                transactionPage
                        .getContent()
                        .stream()
                        .map(ConsumptionConverter::toTransactionInfo)
                        .toList();


        // =========================
        // Response
        // =========================

        return ConsumptionResDTO.GetConsumption.builder()
                .year(year)
                .month(month)

                .totalAmount(totalAmount)

                .transactionCount(
                        (int) transactionCount
                )

                .abnormalCount(
                        (int) abnormalCount
                )

                .uncategorizedCount(
                        (int) unclassifiedCount
                )

                .transactions(transactionList)

                .page(
                        transactionPage.getNumber()
                )

                .size(
                        transactionPage.getSize()
                )

                .totalPages(
                        transactionPage.getTotalPages()
                )

                .totalElements(
                        transactionPage.getTotalElements()
                )

                .hasNext(
                        transactionPage.hasNext()
                )

                .hasPrevious(
                        transactionPage.hasPrevious()
                )

                .build();
    }


    /**
     * 월별 카테고리 지출 조회
     */
    public ConsumptionResDTO.GetConsumptionCategory getConsumptionCategory(
            Integer year,
            Integer month,
            Member member
    ) {

        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDateTime startDate =
                yearMonth.atDay(1).atStartOfDay();

        LocalDateTime endDate =
                yearMonth.plusMonths(1).atDay(1).atStartOfDay();


        List<Object[]> results =
                transactionRepository.findCategorySummary(
                        member,
                        startDate,
                        endDate
                );


        // 전체 지출
        int totalAmount = results.stream()
                .mapToInt(result ->
                        ((Number) result[2]).intValue()
                )
                .sum();


        List<ConsumptionResDTO.CategoryAmount> categories =
                results.stream()
                        .map(result -> {

                            Long categoryId =
                                    ((Number) result[0]).longValue();

                            String categoryName =
                                    (String) result[1];

                            int amount =
                                    ((Number) result[2]).intValue();

                            double percentage =
                                    totalAmount == 0
                                            ? 0
                                            : ((double) amount / totalAmount) * 100;

                            return ConsumptionResDTO.CategoryAmount.builder()
                                    .categoryId(categoryId)
                                    .categoryName(categoryName)
                                    .amount(amount)
                                    .percentage(percentage)
                                    .build();
                        })
                        .toList();


        return ConsumptionResDTO.GetConsumptionCategory.builder()
                .year(year)
                .month(month)
                .categories(categories)
                .build();
    }


    /**
     * 거래 검색
     */
    /**
     * 거래 검색
     */
    /**
     * 거래 검색
     */
    public ConsumptionResDTO.GetConsumption searchConsumption(
            Integer year,
            Integer month,
            String merchant,
            Long categoryId,
            String classificationType,
            int page,
            int size,
            Member member
    ) {

        YearMonth yearMonth =
                YearMonth.of(year, month);

        LocalDateTime startDate =
                yearMonth.atDay(1).atStartOfDay();

        LocalDateTime endDate =
                yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        // =========================
        // Pageable
        // =========================

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "date"
                        )
                );

        // =========================
        // 검색 + 페이징
        // =========================

        Page<Transaction> transactionPage =
                transactionRepository.searchTransactions(
                        member,
                        startDate,
                        endDate,
                        merchant,
                        categoryId,
                        classificationType,
                        pageable
                );

        // =========================
        // 현재 검색 결과 전체 금액
        // =========================

        int totalAmount =
                transactionPage
                        .getContent()
                        .stream()
                        .map(Transaction::getAmount)
                        .filter(amount -> amount != null)
                        .mapToInt(Integer::intValue)
                        .sum();

        // =========================
        // 거래 목록
        // =========================

        List<ConsumptionResDTO.TransactionInfo> transactionList =
                transactionPage
                        .getContent()
                        .stream()
                        .map(ConsumptionConverter::toTransactionInfo)
                        .toList();

        // =========================
        // Response
        // =========================

        return ConsumptionResDTO.GetConsumption.builder()
                .year(year)
                .month(month)
                .totalAmount(totalAmount)
                .transactionCount(
                        (int) transactionPage.getTotalElements()
                )
                .transactions(transactionList)

                .page(
                        transactionPage.getNumber()
                )
                .size(
                        transactionPage.getSize()
                )
                .totalPages(
                        transactionPage.getTotalPages()
                )
                .totalElements(
                        transactionPage.getTotalElements()
                )
                .hasNext(
                        transactionPage.hasNext()
                )
                .hasPrevious(
                        transactionPage.hasPrevious()
                )

                .build();
    }


    /**
     * 거래 상세 조회
     */
    public ConsumptionResDTO.GetConsumptionDetail getConsumptionDetail(
            Long transactionId,
            Member member
    ) {

        Transaction transaction =
                transactionRepository
                        .findByIdAndMember(transactionId, member)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 거래 내역을 찾을 수 없습니다."
                                )
                        );


        return ConsumptionResDTO.GetConsumptionDetail.builder()
                .transactionId(transaction.getId())
                .merchant(transaction.getMerchant())
                .amount(transaction.getAmount())
                .date(transaction.getDate())
                .categoryId(
                        transaction.getCategory() != null
                                ? transaction.getCategory().getId()
                                : null
                )
                .categoryName(
                        transaction.getCategory() != null
                                ? transaction.getCategory().getName()
                                : null
                )
                .classificationType(
                        transaction.getClassificationType()
                )
                .isAbnormal(transaction.getIsAbnormal())
                .confidence(transaction.getConfidence())
                .build();
    }


    /**
     * 거래 삭제
     */
    @Transactional
    public void deleteConsumptionDetail(
            Long transactionId,
            Member member
    ) {

        Transaction transaction =
                transactionRepository
                        .findByIdAndMember(transactionId, member)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 거래 내역을 찾을 수 없습니다."
                                )
                        );

        transactionRepository.delete(transaction);
    }


    /**
     * 카테고리 목록 조회
     */
    public ConsumptionResDTO.GetCategory getCategories(
            Member member
    ) {

        List<Category> categories =
                categoryRepository.findAllByOrderByIdAsc();


        List<ConsumptionResDTO.CategoryInfo> categoryList =
                categories.stream()
                        .map(category ->
                                ConsumptionResDTO.CategoryInfo.builder()
                                        .categoryId(category.getId())
                                        .name(category.getName())
                                        .description(category.getDescription())
                                        .build()
                        )
                        .toList();


        return ConsumptionResDTO.GetCategory.builder()
                .categories(categoryList)
                .build();
    }



    @Transactional
    public Transaction updateCategory(
            Long transactionId,
            Long categoryId,
            Member member
    ) {

        // 1. 거래 조회
        Transaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "거래 내역을 찾을 수 없습니다."
                        )
                );

        // 2. 본인 거래인지 확인
        if (!transaction.getMember().getId().equals(member.getId())) {
            throw new IllegalArgumentException(
                    "본인의 거래만 수정할 수 있습니다."
            );
        }

        // 3. 카테고리 조회
        Category category = categoryRepository
                .findById(categoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "카테고리를 찾을 수 없습니다."
                        )
                );

        // 4. 거래의 카테고리 변경
        transaction.setCategory(category);

        // 5. 사용자가 직접 수정했으므로 USER로 변경
        transaction.setClassificationType(
                ClassificationType.USER
        );

        // 6. 사용자 키워드 생성/수정
        String keyword = transaction.getNormalizedMerchant();

        // normalizedMerchant가 없는 경우 merchant 사용
        if (keyword == null || keyword.isBlank()) {
            keyword = transaction.getMerchant();
        }

        // keyword가 존재하는 경우에만 저장
        if (keyword != null && !keyword.isBlank()) {

            // 7. 기존 사용자 키워드 검색
            MemberCategoryKeyword memberCategoryKeyword =
                    memberCategoryKeywordRepository
                            .findByMemberAndKeyword(member, keyword)
                            .orElse(null);

            if (memberCategoryKeyword == null) {

                // 8. 기존 키워드가 없으면 새로 생성
                memberCategoryKeyword =
                        new MemberCategoryKeyword(
                                member,
                                keyword,
                                category
                        );

                memberCategoryKeywordRepository.save(
                        memberCategoryKeyword
                );

            } else {

                // 9. 기존 키워드가 있으면 카테고리 변경
                memberCategoryKeyword.updateCategory(category);
            }
        }

        // 10. Transaction 반환
        return transaction;
    }
}