package com.example.monivobe.domain.transaction.service;

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
     * ============================================================
     * 예상 지출 서비스
     * ============================================================
     */
    private final ExpectedBudgetService expectedBudgetService;


    /**
     * 거래내역 파일 처리
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
     * 예상 지출 생성
     *
     * @return 이번 파일 업로드로 새롭게 저장된 Transaction 목록
     */
    @Transactional
    public List<Transaction> process(
            Long memberId,
            String fileKey
    ) {

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
             * 예상 지출 생성
             *
             * 중요:
             *
             * 페이지에 들어갈 때마다 생성하는 것이 아니라
             *
             * "파일 업로드가 완료된 시점"
             *
             * 에 생성한다.
             *
             * 거래내역의 날짜를 기준으로
             * 해당 월의 예상 지출을 생성한다.
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
                                    .getExpectedBudget(
                                            member,
                                            targetMonth.getYear(),
                                            targetMonth.getMonthValue()
                                    );

                            System.out.println(
                                    "[EXPECTED BUDGET] "
                                            + targetMonth
                                            + " 예상 지출 생성/조회 완료"
                            );

                        } catch (Exception e) {

                            /*
                             * 예상 지출 생성 실패가
                             * 거래내역 저장 자체를 실패시키지 않도록
                             * 별도로 로그만 남긴다.
                             */
                            System.err.println(
                                    "[EXPECTED BUDGET] "
                                            + targetMonth
                                            + " 예상 지출 생성 실패: "
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
     * Excel → Transaction
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
                 *
                 * 4열(index 3)
                 * 5열(index 4)
                 *
                 * 요구사항:
                 *
                 * 4열이 0이면
                 *      5열의 금액을 INCOME
                 *
                 * 그 외에는
                 *      4열의 금액을 EXPENSE
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
                 * 4열 = 0
                 *
                 * 4열 = 0
                 * 5열 = 100,000
                 *
                 * → INCOME
                 * → amount = 100,000
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

                    /*
                     * =================================================
                     * 일반 지출
                     *
                     * 4열 = 50,000
                     * 5열 = 0
                     *
                     * → EXPENSE
                     * → amount = 50,000
                     * =================================================
                     */

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
                 * INCOME / EXPENSE 모두 Merchant 저장
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
                 * INCOME
                 *      → 소비 카테고리 분류 X
                 *
                 * EXPENSE
                 *      → Keyword
                 *      → UNCONFIRMED
                 *      → LLM
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

                        transaction.setCategory(
                                category
                        );

                        transaction.setClassificationType(
                                ClassificationType.KEYWORD
                        );

                    } else {

                        transaction.setClassificationType(
                                ClassificationType.UNCONFIRMED
                        );
                    }

                } else {

                    /*
                     * =================================================
                     * INCOME
                     *
                     * 소비 카테고리 분류 대상이 아님
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
                );


                transactions.add(
                        transaction
                );
            }
        }

        return transactions;
    }


    /**
     * 날짜 파싱
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
         * 문자열
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
         * Excel 날짜
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
     * 금액 파싱
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
                formatter.formatCellValue(
                        cell
                ).trim();

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
     * 문자열 파싱
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
     * CategoryKeyword 검색
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