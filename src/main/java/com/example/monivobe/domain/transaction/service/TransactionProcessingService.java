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
public class TransactionProcessingService {

    private final TransactionRepository transactionRepository;

    private final MemberRepository memberRepository;

    private final CategoryKeywordRepository
            categoryKeywordRepository;

    private final TransactionAiService
            transactionAiService;

    private final FileStorageService
            fileStorageService;

    private final TransactionOntologyService
            transactionOntologyService;

    private final MerchantService
            merchantService;


    /**
     * 거래내역 파일 처리
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
             * CategoryKeyword는 한 번만 조회
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
             *
             * 이번 파일에서 새롭게 생성된 거래들
             * ====================================================
             */

            List<Transaction> savedTransactions =
                    transactionRepository.saveAll(
                            transactions
                    );


            /*
             * Merchant 연관관계 및 ID를
             * 확실하게 DB에 반영
             */

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
             * 미분류 거래 추출
             * ====================================================
             */

            List<Transaction>
                    unclassifiedTransactions =
                    savedTransactions.stream()

                            .filter(transaction ->
                                    transaction
                                            .getClassificationType()
                                            == ClassificationType
                                            .UNCONFIRMED
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
             * 중요
             *
             * 이번 업로드에서 새롭게 저장된 거래만 반환
             *
             * 기존 Transaction은 반환하지 않음
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
                 * 금액
                 * ====================================================
                 */

                Cell amountCell =
                        row.getCell(4);


                /*
                 * ====================================================
                 * 합계 행 제외
                 *
                 * SUM(E6:E352) 같은 Formula는 거래가 아니므로
                 * 제외
                 * ====================================================
                 */

                if (
                        amountCell != null
                                && amountCell.getCellType()
                                == CellType.FORMULA
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
                 * 거래 타입
                 * ====================================================
                 */

                String transactionTypeValue =
                        getString(
                                row.getCell(1)
                        );

                TransactionType transactionType;

                if (
                        "입금".equals(
                                transactionTypeValue
                        )
                ) {

                    transactionType =
                            TransactionType.INCOME;

                } else {

                    transactionType =
                            TransactionType.EXPENSE;
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


                /*
                 * ====================================================
                 * 금액
                 * ====================================================
                 */

                Integer amount =
                        getInteger(
                                amountCell
                        );


                /*
                 * ====================================================
                 * Transaction 생성
                 *
                 * 생성자에서
                 *
                 * classificationType = UNCLASSIFIED
                 * isAbnormal = false
                 * confidence = false
                 *
                 * 로 초기화됨
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
                 * Merchant 검색
                 *
                 * 1. DB 검색
                 * 2. 없으면 Kakao + Naver
                 * 3. 교차검증
                 * 4. Merchant 저장
                 * 5. Transaction 연결
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
                 * CategoryKeyword 검색
                 * ====================================================
                 */

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


                /*
                 * ====================================================
                 * 새로운 Transaction 목록에 추가
                 * ====================================================
                 */

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