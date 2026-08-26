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
public class TransactionImportService {

    private final TransactionRepository transactionRepository;

    private final MemberRepository memberRepository;

    private final CategoryKeywordRepository categoryKeywordRepository;

    private final FileStorageService fileStorageService;

    private final MerchantService merchantService;


    /**
     * ============================================================
     * 1단계
     *
     * Excel 파싱
     *      ↓
     * Transaction 생성
     *      ↓
     * DB 저장
     *      ↓
     * COMMIT
     *
     * 이 메서드에서는
     *
     * - LLM 분류 X
     * - 이상 지출 X
     * - 예상 지출 X
     * - 정기결제 X
     *
     * 오직 거래내역 저장만 담당한다.
     * ============================================================
     */
    @Transactional
    public List<Transaction> importTransactions(
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
             *
             * 현재 단계에서는
             * 기본적인 카테고리 분류까지만 수행한다.
             *
             * LLM 분류는 다음 단계에서 수행한다.
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
             * 거래내역이 없는 경우
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


            /*
             * ====================================================
             * 즉시 INSERT 실행
             *
             * 실제 COMMIT은 메서드가 정상적으로 종료될 때
             * 발생한다.
             * ====================================================
             */

            transactionRepository.flush();


            System.out.println(
                    "[TRANSACTION IMPORT] "
                            + savedTransactions.size()
                            + "건 거래내역 저장 완료"
            );


            /*
             * ====================================================
             * 여기까지 정상적으로 실행되고 메서드가 종료되면
             *
             * @Transactional
             *      ↓
             * COMMIT
             *
             * 된다.
             * ====================================================
             */

            return savedTransactions;


        } catch (IOException e) {

            /*
             * ====================================================
             * S3 파일 읽기 실패
             *
             * RuntimeException으로 변경해서
             * 현재 트랜잭션을 rollback시킨다.
             * ====================================================
             */

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
             * Excel
             *
             * 0 → 1번째 행
             * 1 → 2번째 행
             * ...
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
                 * index 3 → 입금
                 * index 4 → 지출
                 * ====================================================
                 */

                Cell incomeCell =
                        row.getCell(3);

                Cell expenseCell =
                        row.getCell(4);


                /*
                 * ====================================================
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
                 * 현재 네가 사용하고 있는 Excel 구조 기준
                 *
                 * 입금 컬럼 = 4열
                 * 지출 컬럼 = 5열
                 *
                 * 4열이 0이면
                 *      → INCOME
                 *      → 5열 금액 사용
                 *
                 * 그 외
                 *      → EXPENSE
                 *      → 4열 금액 사용
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
                 * Merchant 저장
                 *
                 * 네이버 / 카카오 API를 이용한
                 * 상세 가게 정보 처리는 다음 단계에서 한다.
                 *
                 * 현재는 기존 Merchant 검색/생성만 수행한다.
                 * ====================================================
                 */



                /*
                 * ====================================================
                 * 기본 Keyword 분류
                 *
                 * 이 단계에서는 LLM을 호출하지 않는다.
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
                        "[TRANSACTION] "
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
         * 문자열 날짜
         *
         * 예:
         * 2026.07.31 21:52:48
         * ============================================================
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
