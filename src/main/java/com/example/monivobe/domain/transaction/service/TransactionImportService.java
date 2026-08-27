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
     * 거래내역 Excel Import
     *
     * Excel
     *   ↓
     * Excel 파싱
     *   ↓
     * Transaction 생성
     *   ↓
     * 거래 유형 판단
     *   ↓
     * Keyword 분류
     *   ↓
     * DB 중복 검사
     *   ↓
     * Excel 내부 중복 검사
     *   ↓
     * DB 저장
     * ============================================================
     *
     * 실제 Excel 구조
     *
     * index 0 = 거래일시
     * index 1 = 적요
     * index 2 = 보낸분/받는분
     * index 3 = 송금메모
     * index 4 = 미사용
     * index 5 = 출금액
     * index 6 = 입금액
     * index 7 = 잔액
     * index 8 = 거래점
     * index 9 = 구분
     *
     * ============================================================
     *
     * 거래 유형
     *
     * 출금액 != 0
     *      → EXPENSE
     *
     * 입금액 != 0
     *      → INCOME
     *
     * 둘 다 0 또는 빈 값
     *      → 거래 제외
     *
     * 둘 다 값이 존재
     *      → 비정상 데이터이므로 거래 제외
     *
     * ============================================================
     *
     * DB 중복 기준
     *
     * member
     * + merchant
     * + amount
     * + date
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
                                        "회원을 찾을 수 없습니다. memberId="
                                                + memberId
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


            log.info(
                    "Excel 파싱 완료 - parsedCount={}",
                    transactions.size()
            );


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
                 * DB 중복 검사
                 *
                 * member
                 * merchant
                 * amount
                 * date
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
                            "DB 중복 거래 제외 - merchant={}, amount={}, date={}, type={}",
                            transaction.getMerchant(),
                            transaction.getAmount(),
                            transaction.getDate(),
                            transaction.getTransactionType()
                    );

                    continue;
                }


                /*
                 * =================================================
                 * 현재 Excel 파일 내부 중복 검사
                 *
                 * 같은 파일에 동일한 거래가 여러 번 들어있는
                 * 경우 한 번만 저장
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
             * ====================================================
             */

            transactionRepository.flush();


            log.info(
                    "Transaction 저장 완료 - count={}",
                    savedTransactions.size()
            );


            log.info(
                    "파싱된 거래={}, 실제 저장={}",
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
     * 실제 Excel 구조
     *
     * index 0 = 거래일시
     * index 1 = 적요
     * index 2 = 보낸분/받는분
     * index 3 = 송금메모
     * index 4 = 미사용
     * index 5 = 출금액
     * index 6 = 입금액
     * index 7 = 잔액
     * index 8 = 거래점
     * index 9 = 구분
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
             * ====================================================
             * 실제 거래 데이터는 6번째 행부터
             *
             * Excel 화면 기준:
             *
             * 1번째 행 → index 0
             * ...
             * 6번째 행 → index 5
             *
             * ====================================================
             */

            for (
                    int i = 5;
                    i <= sheet.getLastRowNum();
                    i++
            ) {

                Row row =
                        sheet.getRow(i);


                /*
                 * ==================================================
                 * 빈 행
                 * ==================================================
                 */

                if (row == null) {

                    log.debug(
                            "빈 행 제외 - row={}",
                            i
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * 날짜
                 *
                 * index 0 = 거래일시
                 * ==================================================
                 */

                LocalDateTime date =
                        getDate(
                                row.getCell(0)
                        );


                if (date == null) {

                    log.debug(
                            "날짜가 없는 행 제외 - row={}",
                            i
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * 거래처
                 *
                 * index 2 = 보낸분/받는분
                 *
                 * 현재 Excel에서
                 *
                 * 체크카드 → 적요에 가맹점
                 *
                 * 스마트출금 → 보낸분/받는분에 이름
                 *
                 * 형태가 섞일 수 있으므로
                 * index 2를 우선 사용
                 * ==================================================
                 */

                String merchant =
                        getString(
                                row.getCell(2)
                        );


                /*
                 * index 2가 비어있는 경우
                 * index 1(적요)을 fallback으로 사용
                 */

                if (
                        merchant == null
                                || merchant.isBlank()
                ) {

                    merchant =
                            getString(
                                    row.getCell(1)
                            );
                }


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
                 * 출금 / 입금 금액
                 *
                 * 중요
                 *
                 * index 5 = 출금액
                 * index 6 = 입금액
                 *
                 * ==================================================
                 */

                Cell expenseCell =
                        row.getCell(5);

                Cell incomeCell =
                        row.getCell(6);


                /*
                 * ==================================================
                 * 합계 행 제외
                 *
                 * 출금액 또는 입금액에 SUM 등의
                 * 수식이 들어있는 경우 제외
                 * ==================================================
                 */

                if (
                        (expenseCell != null
                                && expenseCell.getCellType()
                                == CellType.FORMULA)
                                ||
                                (incomeCell != null
                                        && incomeCell.getCellType()
                                        == CellType.FORMULA)
                ) {

                    log.debug(
                            "합계/수식 행 제외 - row={}",
                            i
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * 출금액
                 * ==================================================
                 */

                Integer expenseAmount =
                        getInteger(
                                expenseCell
                        );


                /*
                 * ==================================================
                 * 입금액
                 * ==================================================
                 */

                Integer incomeAmount =
                        getInteger(
                                incomeCell
                        );


                log.debug(
                        "Excel row={} - merchant={}, expense={}, income={}",
                        i,
                        merchant,
                        expenseAmount,
                        incomeAmount
                );


                /*
                 * ==================================================
                 * 출금과 입금이 동시에 존재하는 경우
                 *
                 * 일반적인 거래에서는 발생하면 안 됨
                 * ==================================================
                 */

                if (
                        expenseAmount != null
                                && expenseAmount != 0
                                && incomeAmount != null
                                && incomeAmount != 0
                ) {

                    log.warn(
                            "출금과 입금 금액이 동시에 존재하는 비정상 거래 제외 - row={}, merchant={}, expense={}, income={}",
                            i,
                            merchant,
                            expenseAmount,
                            incomeAmount
                    );

                    continue;
                }


                /*
                 * ==================================================
                 * 거래 유형 및 금액 결정
                 * ==================================================
                 */

                TransactionType transactionType;

                Integer amount;


                /*
                 * ==================================================
                 * EXPENSE
                 *
                 * index 5 = 출금액
                 * ==================================================
                 */

                if (
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
                 * INCOME
                 *
                 * index 6 = 입금액
                 * ==================================================
                 */

                else if (
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
                 * 금액이 없는 행
                 *
                 * 출금 = 0 / null
                 * 입금 = 0 / null
                 * ==================================================
                 */

                else {

                    log.debug(
                            "입금/출금 금액이 없는 행 제외 - row={}, merchant={}",
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
                 * 찾음
                 *      ↓
                 * KEYWORD
                 *
                 * 못 찾음
                 *      ↓
                 * UNCONFIRMED
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


                    /*
                     * Keyword 발견
                     */

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

                    }


                    /*
                     * Keyword 없음
                     */

                    else {

                        transaction.setClassificationType(
                                ClassificationType.UNCONFIRMED
                        );


                        log.info(
                                "Keyword 분류 실패 → LLM 대상 - merchant={}",
                                merchant
                        );
                    }

                }


                /*
                 * ==================================================
                 * INCOME
                 *
                 * 수입은 소비 카테고리 분류 대상이 아님
                 * ==================================================
                 */

                else {

                    transaction.setClassificationType(
                            ClassificationType.UNCLASSIFIED
                    );
                }


                /*
                 * ==================================================
                 * 최종 거래 로그
                 * ==================================================
                 */

                log.info(
                        "[TRANSACTION] row={}, date={}, merchant={}, amount={}, type={}, classification={}",
                        i,
                        date,
                        merchant,
                        amount,
                        transactionType,
                        transaction.getClassificationType()
                );


                /*
                 * ==================================================
                 * 거래 목록 추가
                 * ==================================================
                 */

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

        /*
         * ========================================================
         * 빈 셀
         * ========================================================
         */

        if (
                cell == null
                        || cell.getCellType()
                        == CellType.BLANK
        ) {

            return null;
        }


        /*
         * ========================================================
         * 문자열 날짜
         *
         * 예:
         *
         * 2026.08.25 14:36:41
         * ========================================================
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


            try {

                return LocalDateTime.parse(
                        value,
                        DateTimeFormatter.ofPattern(
                                "yyyy.MM.dd HH:mm:ss"
                        )
                );

            } catch (Exception e) {

                throw new IllegalArgumentException(
                        "날짜 형식이 올바르지 않습니다: "
                                + value,
                        e
                );
            }
        }


        /*
         * ========================================================
         * Excel 날짜
         * ========================================================
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
     *
     * 예:
     *
     * 12,150
     * 200,000,000
     * 0
     * ============================================================
     */
    private Integer getInteger(
            Cell cell
    ) {

        /*
         * ========================================================
         * 빈 셀
         * ========================================================
         */

        if (cell == null) {

            return null;
        }


        DataFormatter formatter =
                new DataFormatter();


        String value =
                formatter
                        .formatCellValue(cell)
                        .trim();


        /*
         * ========================================================
         * 빈 문자열
         * ========================================================
         */

        if (value.isEmpty()) {

            return null;
        }


        try {

            /*
             * 콤마 제거
             *
             * 200,000,000
             *      ↓
             * 200000000
             */

            value =
                    value.replace(
                            ",",
                            ""
                    );


            /*
             * 숫자로 변환
             */

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
     * 문자열 파싱
     * ============================================================
     */
    private String getString(
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


        return value;
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


            /*
             * 빈 Keyword 무시
             */

            if (
                    keyword == null
                            || keyword.isBlank()
            ) {

                continue;
            }


            /*
             * 가맹점명에 Keyword 포함 여부
             */

            if (
                    merchant.contains(
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