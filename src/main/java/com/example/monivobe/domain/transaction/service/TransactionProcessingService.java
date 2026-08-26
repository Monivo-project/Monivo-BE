package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.abnormal.service.AbnormalService;
import com.example.monivobe.domain.home.service.ExpectedBudgetService;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.CategoryKeyword;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.enums.TransactionType;
import com.example.monivobe.domain.transaction.repository.CategoryKeywordRepository;
import com.example.monivobe.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionProcessingService {

    private final TransactionRepository transactionRepository;

    private final MemberRepository memberRepository;

    private final CategoryKeywordRepository categoryKeywordRepository;

    private final TransactionAiService transactionAiService;

    private final FileStorageService fileStorageService;

    private final TransactionOntologyService transactionOntologyService;

    private final MerchantService merchantService;

    /*
     * 예상 지출 서비스
     */
    private final ExpectedBudgetService expectedBudgetService;

    /*
     * 이상 지출 서비스
     */
    private final AbnormalService abnormalService;


    /**
     * ============================================================
     * 거래내역 파일 처리
     * ============================================================
     *
     * 파일 업로드
     *      ↓
     * Excel 파싱
     *      ↓
     * Transaction 저장
     *      ↓
     * Merchant 저장
     *      ↓
     * Keyword 분류
     *      ↓
     * LLM 분류
     *      ↓
     * 이상 지출 분석
     *      ↓
     * 예상 지출 재계산
     *
     * @return 이번 파일 업로드로 새롭게 저장된 Transaction 목록
     */
    @Transactional
    public List<Transaction> process(
            Long memberId,
            String fileKey
    ) {

        /*
         * ========================================================
         * 회원 조회
         * ========================================================
         */

        Member member =
                memberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "회원을 찾을 수 없습니다."
                                )
                        );


        try (
                InputStream inputStream =
                        fileStorageService.download(fileKey)
        ) {

            /*
             * ====================================================
             * CategoryKeyword 조회
             * ====================================================
             */

            List<CategoryKeyword> keywords =
                    categoryKeywordRepository.findAll();


            /*
             * ====================================================
             * Excel 파싱
             * ====================================================
             */

            List<Transaction> transactions =
                    parseExcel(
                            inputStream,
                            member,
                            keywords
                    );


            /*
             * ====================================================
             * 새로운 거래가 없는 경우
             * ====================================================
             */

            if (transactions.isEmpty()) {

                return List.of();
            }


            /*
             * ====================================================
             * Transaction 저장
             * ====================================================
             */

            List<Transaction> savedTransactions =
                    transactionRepository.saveAll(
                            transactions
                    );

            transactionRepository.flush();


            /*
             * ====================================================
             * Ontology 저장
             * ====================================================
             */

            transactionOntologyService
                    .addTransactions(
                            savedTransactions
                    );


            /*
             * ====================================================
             * 미분류 지출 추출
             *
             * UNCONFIRMED
             * +
             * EXPENSE
             *
             * 인 거래만 LLM 분류
             *
             * INCOME은 LLM 분류하지 않음
             * ====================================================
             */

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


            /*
             * ====================================================
             * LLM 분류
             * ====================================================
             */

            if (!unclassifiedTransactions.isEmpty()) {

                transactionAiService
                        .classifyUnclassifiedTransactions(
                                unclassifiedTransactions
                        );
            }


            /*
             * ====================================================
             * 이상 지출 분석
             * ====================================================
             *
             * 현재 파일에서 새롭게 저장된 거래만 분석
             *
             * AbnormalService 내부에서
             * 기존 거래를 함께 참고하여
             * 이상 지출 여부를 판단한다.
             *
             * EXPENSE만 분석하고
             * INCOME은 분석하지 않는다.
             * ====================================================
             */

            try {

                abnormalService
                        .analyzeNewTransactions(
                                savedTransactions
                        );

                System.out.println(
                        "[ABNORMAL SPENDING] "
                                + "이상 지출 분석 완료"
                );

            } catch (Exception e) {

                /*
                 * 이상 지출 분석 실패 때문에
                 * 거래내역 저장까지 실패하지 않도록
                 * 로그만 출력
                 */

                System.err.println(
                        "[ABNORMAL SPENDING] "
                                + "이상 지출 분석 실패: "
                                + e.getMessage()
                );
            }


            /*
             * ====================================================
             * 예상 지출 재계산
             * ====================================================
             *
             * 이번 파일에 포함된 거래 날짜를 기준으로
             * 해당 월들을 찾는다.
             *
             * 예:
             *
             * 2026-07 거래
             * 2026-08 거래
             *
             * → 2026-07 예상 지출 재계산
             * → 2026-08 예상 지출 재계산
             *
             * 기존 ExpectedBudget이 존재하더라도
             * 새로운 거래가 추가되었기 때문에
             * AI 분석을 다시 수행한다.
             * ====================================================
             */

            savedTransactions.stream()

                    .map(Transaction::getDate)

                    .filter(date -> date != null)

                    .map(YearMonth::from)

                    .distinct()

                    .forEach(targetMonth -> {

                        try {

                            expectedBudgetService
                                    .refreshExpectedBudget(
                                            member,
                                            targetMonth
                                    );

                            System.out.println(
                                    "[EXPECTED BUDGET] "
                                            + targetMonth
                                            + " 예상 지출 재계산 완료"
                            );

                        } catch (Exception e) {

                            /*
                             * 예상 지출 계산 실패가
                             * 거래내역 저장 자체를 실패시키지 않도록
                             * 로그만 남긴다.
                             */

                            System.err.println(
                                    "[EXPECTED BUDGET] "
                                            + targetMonth
                                            + " 예상 지출 재계산 실패: "
                                            + e.getMessage()
                            );
                        }
                    });


            /*
             * ====================================================
             * 이번 업로드에서 새롭게 저장된 거래만 반환
             * ====================================================
             */

            return savedTransactions;

        } catch (IOException e) {

            throw new IllegalArgumentException(
                    "S3 파일을 읽는 중 오류가 발생했습니다.",
                    e
            );
        }
    }


    /**
     * ============================================================
     * Excel → Transaction
     * ============================================================
     */
    private List<Transaction> parseExcel(
            InputStream inputStream,
            Member member,
            List<CategoryKeyword> keywords
    ) throws IOException {

        List<Transaction> transactions =
                new ArrayList<>();


        try (
                Workbook workbook =
                        WorkbookFactory.create(
                                inputStream
                        )
        ) {

            Sheet sheet =
                    workbook.getSheetAt(0);


            /*
             * ====================================================
             * 6번째 행부터 데이터
             *
             * Excel row index
             * 0 → 1번째 행
             * 5 → 6번째 행
             * ====================================================
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


                /*
                 * ====================================================
                 * 금액 컬럼
                 *
                 * index 3 = 4열
                 * index 4 = 5열
                 *
                 * 4열 = 입금
                 * 5열 = 지출
                 * ====================================================
                 */

                Cell incomeCell =
                        row.getCell(3);

                Cell expenseCell =
                        row.getCell(4);


                /*
                 * ====================================================
                 * 합계 행 제외
                 *
                 * SUM 등의 수식이 들어간 행은
                 * 거래내역이 아니므로 제외
                 * ====================================================
                 */

                if (
                        (incomeCell != null
                                && incomeCell.getCellType()
                                == CellType.FORMULA)

                                ||

                                (expenseCell != null
                                        && expenseCell.getCellType()
                                        == CellType.FORMULA)
                ) {

                    continue;
                }


                /*
                 * ====================================================
                 * 날짜
                 * ====================================================
                 */

                LocalDateTime date =
                        getDate(
                                row.getCell(0)
                        );

                if (date == null) {
                    continue;
                }


                /*
                 * ====================================================
                 * 입금 / 지출 금액
                 * ====================================================
                 */

                Integer incomeAmount =
                        getInteger(
                                incomeCell
                        );

                Integer expenseAmount =
                        getInteger(
                                expenseCell
                        );


                TransactionType transactionType;

                Integer amount;


                /*
                 * ====================================================
                 * 입출금 판별
                 *
                 * 4열 = 입금
                 * 5열 = 지출
                 *
                 * 현재 요구사항:
                 *
                 * 4열이 0
                 *      ↓
                 * 5열 금액을 INCOME
                 *
                 * 그 외
                 *      ↓
                 * 4열 금액을 EXPENSE
                 * ====================================================
                 */

                if (
                        incomeAmount != null
                                && incomeAmount == 0
                ) {

                    transactionType =
                            TransactionType.INCOME;

                    amount =
                            expenseAmount;

                } else {

                    transactionType =
                            TransactionType.EXPENSE;

                    amount =
                            incomeAmount;
                }


                /*
                 * ====================================================
                 * 금액이 없는 행 제외
                 * ====================================================
                 */

                if (amount == null) {
                    continue;
                }


                /*
                 * ====================================================
                 * 거래처
                 * ====================================================
                 */

                String merchant =
                        getString(
                                row.getCell(2)
                        );


                if (
                        merchant == null
                                || merchant.isBlank()
                ) {

                    continue;
                }


                /*
                 * ====================================================
                 * Transaction 생성
                 * ====================================================
                 */

                Transaction transaction =
                        new Transaction(
                                member,
                                merchant,
                                amount,
                                date,
                                transactionType
                        );


                /*
                 * ====================================================
                 * Merchant 검색 / 생성
                 *
                 * INCOME / EXPENSE 모두 저장
                 * ====================================================
                 */

                merchantService
                        .findOrCreateMerchant(
                                merchant
                        )
                        .ifPresent(
                                transaction::setMerchantInfo
                        );


                /*
                 * ====================================================
                 * 카테고리 분류
                 *
                 * EXPENSE
                 *      ↓
                 * Keyword 검색
                 *      ↓
                 * 찾음 → KEYWORD
                 * 못 찾음 → UNCONFIRMED
                 *
                 * INCOME
                 *      ↓
                 * UNCLASSIFIED
                 * ====================================================
                 */

                if (
                        transactionType
                                == TransactionType.EXPENSE
                ) {

                    Category category =
                            findCategory(
                                    merchant,
                                    keywords
                            );


                    if (category != null) {

                        /*
                         * Keyword로 카테고리 찾음
                         */

                        transaction.setCategory(
                                category
                        );

                        transaction.setClassificationType(
                                ClassificationType.KEYWORD
                        );

                    } else {

                        /*
                         * Keyword로 찾지 못함
                         *
                         * 이후 LLM 분류 대상
                         */

                        transaction.setClassificationType(
                                ClassificationType.UNCONFIRMED
                        );
                    }

                } else {

                    /*
                     * =================================================
                     * INCOME
                     *
                     * 소비 카테고리 분류하지 않음
                     * =================================================
                     */

                    transaction.setClassificationType(
                            ClassificationType.UNCLASSIFIED
                    );
                }


                /*
                 * ====================================================
                 * 로그
                 * ====================================================
                 */

                System.out.println(
                        "거래 저장: "
                                + "merchant=" + merchant
                                + ", amount=" + amount
                                + ", type=" + transactionType
                                + ", classification="
                                + transaction.getClassificationType()
                );


                transactions.add(
                        transaction
                );
            }
        }

        return transactions;
    }


    /**
     * ============================================================
     * 날짜 파싱
     * ============================================================
     */
    private LocalDateTime getDate(
            Cell cell
    ) {

        if (
                cell == null
                        || cell.getCellType()
                        == CellType.BLANK
        ) {

            return null;
        }


        /*
         * ====================================================
         * 문자열 날짜
         * ====================================================
         */

        if (
                cell.getCellType()
                        == CellType.STRING
        ) {

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


        /*
         * ====================================================
         * Excel 날짜
         * ====================================================
         */

        if (
                cell.getCellType()
                        == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(
                        cell
                )
        ) {

            return cell.getLocalDateTimeCellValue();
        }


        throw new IllegalArgumentException(
                "날짜 형식이 올바르지 않습니다. value="
                        + cell
                        + ", cellType="
                        + cell.getCellType()
        );
    }


    /**
     * ============================================================
     * 금액 파싱
     * ============================================================
     */
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

            /*
             * 콤마 제거
             *
             * 100,000
             *      ↓
             * 100000
             */

            value =
                    value.replace(
                            ",",
                            ""
                    );


            return (int)
                    Double.parseDouble(
                            value
                    );

        } catch (
                NumberFormatException e
        ) {

            throw new IllegalArgumentException(
                    "금액 형식이 올바르지 않습니다: "
                            + value,
                    e
            );
        }
    }


    /**
     * ============================================================
     * 문자열 파싱
     * ============================================================
     */
    private String getString(
            Cell cell
    ) {

        if (cell == null) {
            return null;
        }


        return cell
                .toString()
                .trim();
    }


    /**
     * ============================================================
     * CategoryKeyword 검색
     * ============================================================
     */
    private Category findCategory(
            String merchant,
            List<CategoryKeyword> keywords
    ) {

        if (
                merchant == null
                        || merchant.isBlank()
        ) {

            return null;
        }


        for (
                CategoryKeyword categoryKeyword
                : keywords
        ) {

            String keyword =
                    categoryKeyword.getKeyword();


            if (
                    keyword != null
                            && !keyword.isBlank()
                            && merchant.contains(
                            keyword
                    )
            ) {

                return categoryKeyword
                        .getCategory();
            }
        }


        return null;
    }
}