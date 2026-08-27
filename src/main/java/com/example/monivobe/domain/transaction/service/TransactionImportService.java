package com.example.monivobe.domain.transaction.service;

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
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionImportService {

    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final CategoryKeywordRepository categoryKeywordRepository;
    private final FileStorageService fileStorageService;


    /**
     * ============================================================
     * 1단계
     *
     * Excel
     *   ↓
     * Transaction 생성
     *   ↓
     * 거래 유형 판단
     *   ↓
     * Keyword 분류
     *   ↓
     * 중복 검사
     *   ↓
     * DB 저장
     *
     * ============================================================
     *
     * Excel 구조
     *
     * index 0 = 날짜
     * index 1 = 거래 유형
     * index 2 = 거래처
     * index 3 = 입금
     * index 4 = 지출
     *
     * ============================================================
     *
     * 거래 유형 판단 기준
     *
     * 4열(index 3) != 0
     *      → INCOME
     *
     * 5열(index 4) != 0
     *      → EXPENSE
     *
     * 둘 다 0 또는 빈 값
     *      → 거래 제외
     *
     * 둘 다 값이 존재
     *      → 비정상 데이터이므로 거래 제외
     *
     * ============================================================
     *
     * 중복 기준
     *
     * 회원
     * + 가맹점
     * + 금액
     * + 날짜
     *
     * transactionType은 중복 검사에서 제외
     * ============================================================
     */
    @Transactional
    public List<Transaction> importTransactions(
            Long memberId,
            String fileKey
    ) {

        log.info(
                "========== Transaction Import 시작 =========="
        );

        log.info(
                "memberId={}, fileKey={}",
                memberId,
                fileKey
        );


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

            log.info(
                    "CategoryKeyword 조회 완료 - count={}",
                    keywords.size()
            );


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
             * 거래내역 없음
             * ====================================================
             */

            if (transactions.isEmpty()) {

                log.warn(
                        "import할 거래내역이 없습니다."
                );

                return List.of();
            }


            /*
             * ====================================================
             * 중복 거래 제거
             * ====================================================
             */

            List<Transaction> newTransactions =
                    new ArrayList<>();


            for (Transaction transaction : transactions) {


                /*
                 * =================================================
                 * DB에 이미 존재하는 거래인지 확인
                 *
                 * 기준:
                 *
                 * member
                 * merchant
                 * amount
                 * date
                 *
                 * transactionType은 사용하지 않음
                 * =================================================
                 */

                boolean duplicated =
                        transactionRepository
                                .existsByMemberAndMerchantAndAmountAndDate(
                                        member,
                                        transaction.getMerchant(),
                                        transaction.getAmount(),
                                        transaction.getDate()
                                );


                /*
                 * =================================================
                 * DB에 이미 존재
                 * =================================================
                 */

                if (duplicated) {

                    log.info(
                            "중복 거래 제외 - merchant={}, amount={}, date={}, type={}",
                            transaction.getMerchant(),
                            transaction.getAmount(),
                            transaction.getDate(),
                            transaction.getTransactionType()
                    );

                    continue;
                }


                /*
                 * =================================================
                 * 현재 Excel 파일 내부에서 중복인지 확인
                 *
                 * 같은 파일에 동일한 거래가 여러 번 들어있는
                 * 경우에도 한 번만 저장
                 *
                 * transactionType은 검사하지 않음
                 * =================================================
                 */

                boolean duplicatedInCurrentFile =
                        newTransactions.stream()
                                .anyMatch(existing ->
                                        isSameTransaction(
                                                existing,
                                                transaction
                                        )
                                );


                if (duplicatedInCurrentFile) {

                    log.info(
                            "Excel 내부 중복 거래 제외 - merchant={}, amount={}, date={}, type={}",
                            transaction.getMerchant(),
                            transaction.getAmount(),
                            transaction.getDate(),
                            transaction.getTransactionType()
                    );

                    continue;
                }


                /*
                 * =================================================
                 * 신규 거래 목록에 추가
                 * =================================================
                 */

                newTransactions.add(
                        transaction
                );
            }


            /*
             * ====================================================
             * 저장할 거래가 없는 경우
             * ====================================================
             */

            if (newTransactions.isEmpty()) {

                log.info(
                        "새로운 거래가 없습니다. 모든 거래가 중복입니다."
                );

                return List.of();
            }


            /*
             * ====================================================
             * DB 저장
             * ====================================================
             */

            List<Transaction> savedTransactions =
                    transactionRepository.saveAll(
                            newTransactions
                    );


            /*
             * INSERT 즉시 실행
             */

            transactionRepository.flush();


            log.info(
                    "Transaction 저장 완료 - count={}",
                    savedTransactions.size()
            );


            log.info(
                    "중복 제외 전={}, 실제 저장={}",
                    transactions.size(),
                    savedTransactions.size()
            );


            log.info(
                    "========== Transaction Import 완료 =========="
            );


            return savedTransactions;


        } catch (IOException e) {

            log.error(
                    "S3 파일 읽기 실패 - fileKey={}",
                    fileKey,
                    e
            );

            throw new IllegalArgumentException(
                    "S3 파일을 읽는 중 오류가 발생했습니다.",
                    e
            );
        }
    }


    /**
     * ============================================================
     * 거래 동일 여부
     *
     * Excel 내부 중복 검사
     *
     * 기준:
     *
     * merchant
     * + amount
     * + date
     *
     * transactionType은 검사하지 않음
     * ============================================================
     */
    private boolean isSameTransaction(
            Transaction first,
            Transaction second
    ) {

        if (
                first == null
                        || second == null
        ) {

            return false;
        }


        return equals(
                first.getMerchant(),
                second.getMerchant()
        )
                && equals(
                first.getAmount(),
                second.getAmount()
        )
                && equals(
                first.getDate(),
                second.getDate()
        );
    }


    /**
     * ============================================================
     * 안전한 equals
     * ============================================================
     */
    private boolean equals(
            Object first,
            Object second
    ) {

        if (first == null) {

            return second == null;
        }

        return first.equals(second);
    }


    /**
     * ============================================================
     * Excel → Transaction
     *
     * Excel 구조
     *
     * index 0 = 날짜
     * index 1 = 거래 유형
     * index 2 = 거래처
     * index 3 = 입금
     * index 4 = 지출
     *
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


            log.info(
                    "Excel sheet={}, lastRow={}",
                    sheet.getSheetName(),
                    sheet.getLastRowNum()
            );


            /*
             * 실제 거래 데이터는 6번째 행부터
             * index = 5
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
                 * ==================================================
                 * 날짜
                 * ==================================================
                 */

                LocalDateTime date =
                        getDate(
                                row.getCell(0)
                        );


                if (date == null) {

                    continue;
                }


                /*
                 * ==================================================
                 * 거래처
                 * ==================================================
                 */

                String merchant =
                        getString(
                                row.getCell(2)
                        );


                if (
                        merchant == null
                                || merchant.isBlank()
                ) {

                    log.warn(
                            "거래처명이 없는 거래 - row={}",
                            i
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * 입금 / 지출 금액 확인
                 *
                 * index 3 = 입금
                 * index 4 = 지출
                 * ==================================================
                 */

                Cell incomeCell =
                        row.getCell(3);

                Cell expenseCell =
                        row.getCell(4);


                /*
                 * ==================================================
                 * SUM 등의 수식 행 제외
                 *
                 * 입금 또는 지출 컬럼 중 하나라도
                 * 수식이면 합계 행으로 판단
                 * ==================================================
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

                    log.debug(
                            "합계 행 제외 - row={}",
                            i
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * 입금 금액
                 * ==================================================
                 */

                Integer incomeAmount =
                        getInteger(
                                incomeCell
                        );


                /*
                 * ==================================================
                 * 지출 금액
                 * ==================================================
                 */

                Integer expenseAmount =
                        getInteger(
                                expenseCell
                        );


                /*
                 * ==================================================
                 * 거래 유형 판단
                 *
                 * 4열 != 0 → INCOME
                 * 5열 != 0 → EXPENSE
                 * ==================================================
                 */

                TransactionType transactionType;

                Integer amount;


                /*
                 * ==================================================
                 * 둘 다 값이 있는 경우
                 *
                 * 일반적인 거래 데이터에서는 발생하면 안 됨
                 * ==================================================
                 */

                if (
                        incomeAmount != null
                                && incomeAmount != 0
                                && expenseAmount != null
                                && expenseAmount != 0
                ) {

                    log.warn(
                            "입금과 지출 금액이 동시에 존재하는 비정상 거래 제외 - row={}, merchant={}, income={}, expense={}",
                            i,
                            merchant,
                            incomeAmount,
                            expenseAmount
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * INCOME
                 *
                 * 4열(index 3)이 0이 아닌 경우
                 * ==================================================
                 */

                if (
                        incomeAmount != null
                                && incomeAmount != 0
                ) {

                    transactionType =
                            TransactionType.INCOME;

                    amount =
                            incomeAmount;

                }


                /*
                 * ==================================================
                 * EXPENSE
                 *
                 * 5열(index 4)이 0이 아닌 경우
                 * ==================================================
                 */

                else if (
                        expenseAmount != null
                                && expenseAmount != 0
                ) {

                    transactionType =
                            TransactionType.EXPENSE;

                    amount =
                            expenseAmount;

                }


                /*
                 * ==================================================
                 * 금액이 없는 행
                 *
                 * 입금 = 0
                 * 지출 = 0
                 * ==================================================
                 */

                else {

                    log.debug(
                            "입금/지출 금액이 없는 행 제외 - row={}, merchant={}",
                            i,
                            merchant
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * Transaction 생성
                 * ==================================================
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
                 * ==================================================
                 * Keyword 기반 Category 분류
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
                 * ==================================================
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


                        log.info(
                                "Keyword 분류 성공 - merchant={}, category={}",
                                merchant,
                                category.getName()
                        );

                    } else {

                        transaction.setClassificationType(
                                ClassificationType.UNCONFIRMED
                        );


                        log.info(
                                "Keyword 분류 실패 → LLM 대상 - merchant={}",
                                merchant
                        );
                    }

                } else {

                    /*
                     * INCOME은 소비 카테고리 분류 대상이 아니므로
                     * UNCLASSIFIED 처리
                     */

                    transaction.setClassificationType(
                            ClassificationType.UNCLASSIFIED
                    );
                }


                /*
                 * ==================================================
                 * 로그
                 * ==================================================
                 */

                log.info(
                        "[TRANSACTION] row={}, merchant={}, amount={}, type={}, classification={}",
                        i,
                        merchant,
                        amount,
                        transactionType,
                        transaction.getClassificationType()
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
     * 날짜
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
         * 문자열 날짜
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
     * ============================================================
     * 금액
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


    /**
     * ============================================================
     * 문자열
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
                    keyword == null
                            || keyword.isBlank()
            ) {

                continue;
            }


            if (
                    merchant.contains(keyword)
            ) {

                return categoryKeyword
                        .getCategory();
            }
        }


        return null;
    }
}