package com.example.monivobe.domain.home.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.domain.transaction.dto.response.AiResponse;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.CategoryKeyword;
import com.example.monivobe.domain.transaction.entity.Merchant;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.enums.TransactionType;
import com.example.monivobe.domain.transaction.repository.CategoryKeywordRepository;
import com.example.monivobe.domain.transaction.repository.CategoryRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import com.example.monivobe.domain.transaction.service.MerchantService;
import com.example.monivobe.domain.transaction.service.TransactionOntologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TestService {

    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryKeywordRepository categoryKeywordRepository;
    private final ChatClient chatClient;

    /*
     * Ontology
     */
    private final TransactionOntologyService transactionOntologyService;

    /*
     * Merchant
     *
     * 거래처명
     * ↓
     * DB 조회
     * ↓
     * 없으면 Kakao + Naver
     * ↓
     * 교차검증
     * ↓
     * Merchant 저장
     */
    private final MerchantService merchantService;


    // ============================================================
    // Excel Import
    // ============================================================

    @Transactional
    public void importExcel(
            MultipartFile file,
            Long memberId
    ) throws IOException {

        log.info(
                "========== Excel 거래내역 import 시작 =========="
        );

        // ========================================================
        // 1. 회원 조회
        // ========================================================

        Member member =
                memberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "회원을 찾을 수 없습니다."
                                )
                        );

        // ========================================================
        // 2. 거래내역 목록
        // ========================================================

        List<Transaction> transactions =
                new ArrayList<>();

        // ========================================================
        // 3. Merchant 정보 저장
        //
        // key   : 거래처명
        // value : Merchant
        //
        // LLM 단계에서 다시 API를 호출하지 않기 위해 사용
        // ========================================================

        Map<String, Merchant> merchantMap =
                new HashMap<>();

        // ========================================================
        // 4. Category Keyword 조회
        // ========================================================

        List<CategoryKeyword> keywords =
                categoryKeywordRepository.findAll();

        log.info(
                "CategoryKeyword 조회 완료 - count={}",
                keywords.size()
        );

        // ========================================================
        // 5. Excel 읽기
        // ========================================================

        try (
                InputStream inputStream =
                        file.getInputStream();

                Workbook workbook =
                        WorkbookFactory.create(inputStream)
        ) {

            Sheet sheet =
                    workbook.getSheetAt(0);

            log.info(
                    "Excel sheet={} / lastRow={}",
                    sheet.getSheetName(),
                    sheet.getLastRowNum()
            );

            /*
             * 6번째 행(index 5)부터 실제 거래 데이터
             */
            for (
                    int i = 5;
                    i <= sheet.getLastRowNum();
                    i++
            ) {

                Row row =
                        sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                // =================================================
                // 금액
                // =================================================

                Cell amountCell =
                        row.getCell(4);

                /*
                 * 합계 행 제외
                 */
                if (amountCell != null
                        && amountCell.getCellType()
                        == CellType.FORMULA) {

                    log.debug(
                            "합계 행 제외 - row={}",
                            i
                    );

                    continue;
                }

                // =================================================
                // 날짜
                // =================================================

                LocalDateTime date =
                        getDate(
                                row.getCell(0)
                        );

                if (date == null) {
                    continue;
                }

                // =================================================
                // 거래 유형
                // =================================================

                String transactionTypeValue =
                        getString(
                                row.getCell(1)
                        );

                TransactionType transactionType;

                if ("입금".equals(transactionTypeValue)) {

                    transactionType =
                            TransactionType.INCOME;

                } else {

                    transactionType =
                            TransactionType.EXPENSE;
                }

                // =================================================
                // 거래처
                // =================================================

                String merchantName =
                        getString(
                                row.getCell(2)
                        );

                if (merchantName == null
                        || merchantName.isBlank()) {

                    log.warn(
                            "거래처명이 없는 거래 - row={}",
                            i
                    );

                    continue;
                }

                // =================================================
                // 금액
                // =================================================

                Integer amount =
                        getInteger(
                                amountCell
                        );

                // =================================================
                // Transaction 생성
                // =================================================

                Transaction transaction =
                        new Transaction(
                                member,
                                merchantName,
                                amount,
                                date,
                                transactionType
                        );

                // =================================================
                // Merchant 검색
                // =================================================

                /*
                 * 같은 거래처가 Excel에 여러 번 등장하더라도
                 *
                 * 첫 번째:
                 * Kakao + Naver 검색
                 *
                 * 이후:
                 * merchantMap에서 바로 사용
                 *
                 * 하도록 한다.
                 */

                String merchantKey =
                        normalizeMerchant(
                                merchantName
                        );

                Merchant merchant =
                        merchantMap.get(
                                merchantKey
                        );

                if (merchant == null) {

                    try {

                        log.info(
                                "========== Merchant 검색 시작 =========="
                        );

                        log.info(
                                "merchantName={}",
                                merchantName
                        );

                        Optional<Merchant> merchantOptional =
                                merchantService
                                        .findOrCreateMerchant(
                                                merchantName
                                        );

                        if (merchantOptional.isPresent()) {

                            merchant =
                                    merchantOptional.get();

                            merchantMap.put(
                                    merchantKey,
                                    merchant
                            );

                            log.info(
                                    "Merchant 검색 성공"
                            );

                            log.info(
                                    "name={}",
                                    merchant.getName()
                            );

                            log.info(
                                    "businessType={}",
                                    merchant.getBusinessType()
                            );

                            log.info(
                                    "businessCategory={}",
                                    merchant.getBusinessCategory()
                            );

                            log.info(
                                    "address={}",
                                    merchant.getAddress()
                            );

                            log.info(
                                    "roadAddress={}",
                                    merchant.getRoadAddress()
                            );

                            log.info(
                                    "source={}",
                                    merchant.getSource()
                            );

                            log.info(
                                    "confidence={}",
                                    merchant.getConfidence()
                            );

                        } else {

                            log.warn(
                                    "Merchant 검색 결과 없음 - merchant={}",
                                    merchantName
                            );
                        }

                    } catch (Exception e) {

                        /*
                         * Merchant 검색 실패 때문에
                         * Excel 전체 import가 실패하지 않도록 한다.
                         */

                        log.error(
                                "Merchant 검색 오류 - merchant={}",
                                merchantName,
                                e
                        );
                    }

                } else {

                    log.info(
                            "Merchant 캐시 사용 - merchant={}",
                            merchantName
                    );
                }

                // =================================================
                // Keyword 기반 Category 분류
                // =================================================

                Category category =
                        findCategory(
                                merchantName,
                                keywords
                        );

                if (category != null) {

                    transaction.setCategory(
                            category
                    );

                    transaction.setClassificationType(
                            ClassificationType.KEYWORD
                    );

                    log.info(
                            "Keyword 분류 성공 - merchant={}, category={}",
                            merchantName,
                            category.getName()
                    );

                } else {

                    transaction.setClassificationType(
                            ClassificationType.UNCONFIRMED
                    );

                    log.info(
                            "Keyword 분류 실패 → LLM 대상 - merchant={}",
                            merchantName
                    );
                }

                transactions.add(
                        transaction
                );
            }
        }

        // ========================================================
        // 6. 거래내역 없음
        // ========================================================

        if (transactions.isEmpty()) {

            log.warn(
                    "import할 거래내역이 없습니다."
            );

            return;
        }

        // ========================================================
        // 7. Transaction MySQL 저장
        // ========================================================

        List<Transaction> savedTransactions =
                transactionRepository.saveAll(
                        transactions
                );

        /*
         * 실제 INSERT가 필요한 경우 flush
         */
        transactionRepository.flush();

        log.info(
                "Transaction 저장 완료 - count={}",
                savedTransactions.size()
        );

        // ========================================================
        // 8. Ontology 저장
        // ========================================================

        transactionOntologyService.addTransactions(
                savedTransactions
        );

        log.info(
                "Ontology Transaction 저장 완료"
        );

        // ========================================================
        // 9. 미분류 지출 추출
        // ========================================================

        List<Transaction> unclassifiedTransactions =
                savedTransactions.stream()
                        .filter(transaction ->
                                transaction.getClassificationType()
                                        == ClassificationType.UNCONFIRMED
                        )
                        .filter(transaction ->
                                transaction.getTransactionType()
                                        == TransactionType.EXPENSE
                        )
                        .toList();

        log.info(
                "LLM 분류 대상 거래 수={}",
                unclassifiedTransactions.size()
        );

        // ========================================================
        // 10. LLM 분류
        // ========================================================

        if (!unclassifiedTransactions.isEmpty()) {

            classifyUnclassifiedTransactions(
                    unclassifiedTransactions,
                    merchantMap
            );
        }

        log.info(
                "========== Excel 거래내역 import 완료 =========="
        );
    }


    // ============================================================
    // LLM 분류
    // ============================================================

    private void classifyUnclassifiedTransactions(
            List<Transaction> transactions,
            Map<String, Merchant> merchantMap
    ) {

        if (transactions == null
                || transactions.isEmpty()) {

            return;
        }

        log.info(
                "LLM 분류 시작 - count={}",
                transactions.size()
        );

        // ========================================================
        // 1. LLM 호출
        // ========================================================

        List<AiResponse.TransactionClassification> results =
                classifyByLlm(
                        transactions,
                        merchantMap
                );

        if (results == null
                || results.isEmpty()) {

            log.warn(
                    "LLM 분류 결과가 없습니다."
            );

            return;
        }

        log.info(
                "LLM 응답 결과 수={}",
                results.size()
        );

        // ========================================================
        // 2. LLM 결과 적용
        // ========================================================

        for (
                AiResponse.TransactionClassification result
                : results
        ) {

            if (result == null
                    || result.transactionId() == null) {

                continue;
            }

            Transaction transaction =
                    transactionRepository
                            .findById(
                                    result.transactionId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "거래내역을 찾을 수 없습니다. id="
                                                    + result.transactionId()
                                    )
                            );

            // ====================================================
            // INCOME 방어
            // ====================================================

            if (transaction.getTransactionType()
                    != TransactionType.EXPENSE) {

                log.warn(
                        "입금 거래가 LLM 결과에 포함됨 - transactionId={}",
                        transaction.getId()
                );

                continue;
            }

            // ====================================================
            // 신뢰도 검사
            // ====================================================

            if (result.confidence() == null
                    || result.confidence() < 0.7
                    || result.categoryId() == null) {

                transaction.setClassificationType(
                        ClassificationType.UNCONFIRMED
                );

                log.info(
                        "LLM 분류 실패 - " +
                                "transactionId={}, confidence={}, categoryId={}, reason={}",
                        transaction.getId(),
                        result.confidence(),
                        result.categoryId(),
                        result.reason()
                );

                transactionOntologyService
                        .updateClassification(
                                transaction
                        );

                continue;
            }

            // ====================================================
            // Category 조회
            // ====================================================

            Category category =
                    categoryRepository
                            .findById(
                                    result.categoryId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "카테고리를 찾을 수 없습니다. id="
                                                    + result.categoryId()
                                    )
                            );

            // ====================================================
            // Category 설정
            // ====================================================

            transaction.setCategory(
                    category
            );

            transaction.setClassificationType(
                    ClassificationType.LLM
            );

            log.info(
                    "LLM 분류 완료 - " +
                            "transactionId={}, merchant={}, category={}, confidence={}, reason={}",
                    transaction.getId(),
                    transaction.getMerchant(),
                    category.getName(),
                    result.confidence(),
                    result.reason()
            );

            // ====================================================
            // Ontology 업데이트
            // ====================================================

            transactionOntologyService
                    .updateClassification(
                            transaction
                    );
        }
    }


    // ============================================================
    // LLM 호출
    // ============================================================

    private List<AiResponse.TransactionClassification> classifyByLlm(
            List<Transaction> transactions,
            Map<String, Merchant> merchantMap
    ) {

        if (transactions == null
                || transactions.isEmpty()) {

            return List.of();
        }

        // ========================================================
        // Category 조회
        // ========================================================

        List<Category> categories =
                categoryRepository.findAll();

        // ========================================================
        // Category 정보
        // ========================================================

        String categoryInfo =
                categories.stream()
                        .map(category ->
                                category.getId()
                                        + ": "
                                        + category.getName()
                                        + " - "
                                        + category.getDescription()
                        )
                        .collect(
                                Collectors.joining("\n")
                        );

        // ========================================================
        // Transaction + Merchant 정보
        // ========================================================

        String transactionInfo =
                transactions.stream()
                        .map(transaction ->
                                createTransactionInfo(
                                        transaction,
                                        merchantMap
                                )
                        )
                        .collect(
                                Collectors.joining("\n\n")
                        );

        // ========================================================
        // Prompt
        // ========================================================

        String prompt =
                """
                당신은 사용자의 소비내역을 분석하고
                소비 카테고리를 분류하는 AI입니다.

                거래처명만 보고 판단하지 마세요.

                제공되는 Merchant 정보를 적극적으로 활용하세요.

                특히 다음 정보를 중요하게 사용하세요.

                - businessType
                - businessCategory
                - address
                - roadAddress
                - source
                - confidence


                예를 들어 다음과 같은 정보가 있다고 가정합니다.

                거래처명: 일락

                [Merchant 정보]
                merchantName: 일락
                businessType: 음식점
                businessCategory: 음식점>한식
                source: BOTH
                confidence: 0.95

                이 경우 "일락"이라는 이름이 생소하더라도
                Merchant의 businessType이 "음식점"이고
                businessCategory가 "음식점>한식"이므로
                식비 카테고리로 분류해야 합니다.


                [카테고리]

                %s


                [거래내역 + Merchant 정보]

                %s


                다음 기준을 반드시 지켜주세요.

                1. 거래처명만으로 판단하지 마세요.

                2. Merchant 정보가 있으면
                   Merchant 정보를 우선적으로 활용하세요.

                3. businessType이 "음식점"이면
                   식비 카테고리를 강하게 고려하세요.

                4. businessCategory가
                   "음식점>한식",
                   "음식점>중식",
                   "음식점>일식",
                   "음식점>양식",
                   "음식점>분식"
                   등이라면 식비로 판단하세요.

                5. businessType이 "카페"이면
                   식비로 판단하세요.

                6. 음식점에서 발생한 일반적인 외식 결제는
                   식비로 분류하세요.

                7. businessType이 쇼핑 관련 업종이면
                   쇼핑/생활 카테고리를 고려하세요.

                8. 편의점, 생활용품점, 온라인 쇼핑몰,
                   의류매장 등은 쇼핑/생활 카테고리를 고려하세요.

                9. 병원, 의원, 약국 등은
                   의료/건강 카테고리를 고려하세요.

                10. 택시, 버스, 지하철, 주유소 등은
                    교통 카테고리를 고려하세요.

                11. 영화관, 공연장, 게임, OTT 등은
                    여가/문화 카테고리를 고려하세요.

                12. 거래처 업종과 금액을 함께 고려하세요.

                13. 반드시 위 카테고리 중 하나의
                    categoryId를 선택하세요.

                14. confidence는 0.0 ~ 1.0 사이의 값입니다.

                15. Merchant 정보가 명확하고
                    업종이 확실하다면 높은 confidence를 주세요.

                16. confidence가 0.7 미만이면
                    categoryId를 null로 반환하세요.

                17. reason에는 분류 근거를 간단하게 작성하세요.

                18. 현재 전달되는 모든 거래내역은
                    EXPENSE(지출) 거래입니다.

                19. transactionId는 전달받은 값을
                    그대로 사용하세요.

                20. Merchant 정보가 없는 경우에는
                    거래처명과 금액을 기준으로 판단하세요.

                반드시 JSON 형식으로 반환하세요.
                """
                        .formatted(
                                categoryInfo,
                                transactionInfo
                        );

        log.info(
                "========== LLM Prompt ==========\n{}",
                prompt
        );

        // ========================================================
        // LLM 호출
        // ========================================================

        List<AiResponse.TransactionClassification> result =
                chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .entity(
                                new ParameterizedTypeReference<
                                        List<AiResponse.TransactionClassification>
                                        >() {
                                }
                        );

        return result != null
                ? result
                : List.of();
    }


    // ============================================================
    // Transaction + Merchant 정보
    // ============================================================

    private String createTransactionInfo(
            Transaction transaction,
            Map<String, Merchant> merchantMap
    ) {

        String merchantName =
                transaction.getMerchant();

        String merchantKey =
                normalizeMerchant(
                        merchantName
                );

        Merchant merchant =
                merchantMap.get(
                        merchantKey
                );

        // ========================================================
        // Merchant 정보 있음
        // ========================================================

        if (merchant != null) {

            log.info(
                    "LLM에 Merchant 정보 전달 - " +
                            "merchant={}, businessType={}, businessCategory={}",
                    merchant.getName(),
                    merchant.getBusinessType(),
                    merchant.getBusinessCategory()
            );

            return """
                    transactionId: %d
                    merchant: %s
                    amount: %d
                    date: %s

                    [Merchant 정보]
                    merchantName: %s
                    businessType: %s
                    businessCategory: %s
                    address: %s
                    roadAddress: %s
                    telephone: %s
                    source: %s
                    confidence: %s
                    """.formatted(
                    transaction.getId(),
                    nullToEmpty(
                            transaction.getMerchant()
                    ),
                    transaction.getAmount(),
                    transaction.getDate(),

                    nullToEmpty(
                            merchant.getName()
                    ),
                    nullToEmpty(
                            merchant.getBusinessType()
                    ),
                    nullToEmpty(
                            merchant.getBusinessCategory()
                    ),
                    nullToEmpty(
                            merchant.getAddress()
                    ),
                    nullToEmpty(
                            merchant.getRoadAddress()
                    ),
                    nullToEmpty(
                            merchant.getTelephone()
                    ),
                    nullToEmpty(
                            merchant.getSource()
                    ),
                    merchant.getConfidence()
            );
        }

        // ========================================================
        // Merchant 정보 없음
        // ========================================================

        log.warn(
                "LLM에 전달할 Merchant 정보 없음 - merchant={}",
                merchantName
        );

        return """
                transactionId: %d
                merchant: %s
                amount: %d
                date: %s

                [Merchant 정보]
                Merchant 정보를 확인할 수 없습니다.
                거래처명과 금액을 기준으로 판단하세요.
                """.formatted(
                transaction.getId(),
                nullToEmpty(
                        transaction.getMerchant()
                ),
                transaction.getAmount(),
                transaction.getDate()
        );
    }


    // ============================================================
    // 날짜 파싱
    // ============================================================

    private LocalDateTime getDate(
            Cell cell
    ) {

        if (cell == null
                || cell.getCellType()
                == CellType.BLANK) {

            return null;
        }

        if (cell.getCellType()
                == CellType.STRING) {

            String value =
                    cell.getStringCellValue()
                            .trim();

            if (value.isEmpty()) {
                return null;
            }

            return LocalDateTime.parse(
                    value,
                    DateTimeFormatter.ofPattern(
                            "yyyy.MM.dd HH:mm:ss"
                    )
            );
        }

        if (cell.getCellType()
                == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {

            return cell.getLocalDateTimeCellValue();
        }

        throw new IllegalArgumentException(
                "날짜 형식이 올바르지 않습니다. "
                        + "value="
                        + cell.toString()
                        + ", cellType="
                        + cell.getCellType()
        );
    }


    // ============================================================
    // 금액 파싱
    // ============================================================

    private Integer getInteger(
            Cell cell
    ) {

        if (cell == null) {
            return null;
        }

        DataFormatter formatter =
                new DataFormatter();

        String value =
                formatter
                        .formatCellValue(cell)
                        .trim();

        if (value.isEmpty()) {
            return null;
        }

        try {

            value =
                    value.replace(
                            ",",
                            ""
                    );

            return (int)
                    Double.parseDouble(
                            value
                    );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "금액 형식이 올바르지 않습니다: "
                            + value,
                    e
            );
        }
    }


    // ============================================================
    // 문자열 파싱
    // ============================================================

    private String getString(
            Cell cell
    ) {

        if (cell == null) {
            return null;
        }

        return cell.toString().trim();
    }


    // ============================================================
    // Keyword Category 검색
    // ============================================================

    private Category findCategory(
            String merchant,
            List<CategoryKeyword> keywords
    ) {

        if (merchant == null
                || merchant.isBlank()) {

            return null;
        }

        for (
                CategoryKeyword categoryKeyword
                : keywords
        ) {

            String keyword =
                    categoryKeyword.getKeyword();

            if (keyword == null
                    || keyword.isBlank()) {

                continue;
            }

            if (merchant.contains(keyword)) {

                return categoryKeyword.getCategory();
            }
        }

        return null;
    }


    // ============================================================
    // Merchant 이름 정규화
    // ============================================================

    private String normalizeMerchant(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\s+", "")
                .replaceAll(
                        "[^가-힣a-zA-Z0-9]",
                        ""
                )
                .toLowerCase();
    }


    // ============================================================
    // Null 처리
    // ============================================================

    private String nullToEmpty(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }
}