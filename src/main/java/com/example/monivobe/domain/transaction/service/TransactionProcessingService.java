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
    private final CategoryKeywordRepository categoryKeywordRepository;
    private final TransactionAiService transactionAiService;
    private final FileStorageService fileStorageService;

    /**
     * S3에 저장된 거래내역 Excel 파일 처리
     *
     * 실제 호출은 TransactionUploadedEventListener에서
     * 비동기로 수행된다.
     */
    @Transactional
    public void process(
            Long memberId,
            String fileKey
    ) {

        // 1. 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "회원을 찾을 수 없습니다."
                        )
                );

        // 2. S3에서 파일 다운로드
        try (
                InputStream inputStream =
                        fileStorageService.download(fileKey)
        ) {

            // 3. 카테고리 키워드는 한 번만 조회
            List<CategoryKeyword> keywords =
                    categoryKeywordRepository.findAll();

            // 4. Excel 파싱
            List<Transaction> transactions =
                    parseExcel(
                            inputStream,
                            member,
                            keywords
                    );

            if (transactions.isEmpty()) {
                return;
            }

            // 5. 먼저 DB 저장
            transactionRepository.saveAll(
                    transactions
            );

            // 6. 미확인 거래만 추출
            List<Transaction> unclassifiedTransactions =
                    transactions.stream()
                            .filter(transaction ->
                                    transaction.getClassificationType()
                                            == ClassificationType.UNCONFIRMED
                            )
                            .toList();

            // 7. 미확인 거래만 AI 분류
            if (!unclassifiedTransactions.isEmpty()) {

                transactionAiService
                        .classifyUnclassifiedTransactions(
                                unclassifiedTransactions
                        );
            }

        } catch (IOException e) {

            throw new IllegalArgumentException(
                    "S3 파일을 읽는 중 오류가 발생했습니다.",
                    e
            );
        }
    }

    /**
     * Excel 파일을 Transaction으로 변환
     */
    private List<Transaction> parseExcel(
            InputStream inputStream,
            Member member,
            List<CategoryKeyword> keywords
    ) throws IOException {

        List<Transaction> transactions =
                new ArrayList<>();

        try (Workbook workbook =
                     WorkbookFactory.create(inputStream)) {

            Sheet sheet =
                    workbook.getSheetAt(0);

            /*
             * 6번째 행(index 5)부터 데이터 시작
             */
            for (int i = 5;
                 i <= sheet.getLastRowNum();
                 i++) {

                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                /*
                 * 5번째 컬럼(index 4)을 금액으로 사용
                 */
                Cell amountCell =
                        row.getCell(4);

                /*
                 * 합계 행 제외
                 */
                if (amountCell != null
                        && amountCell.getCellType()
                        == CellType.FORMULA) {

                    continue;
                }

                /*
                 * 날짜
                 */
                LocalDateTime date =
                        getDate(row.getCell(0));

                if (date == null) {
                    continue;
                }

                /*
                 * 입금 / 출금 구분
                 *
                 * Excel의 2번째 컬럼(index 1)이
                 * "입금"이면 INCOME
                 * 그 외에는 EXPENSE
                 */
                String transactionTypeValue =
                        getString(row.getCell(1));

                TransactionType transactionType;

                if ("입금".equals(transactionTypeValue)) {

                    transactionType =
                            TransactionType.INCOME;

                } else {

                    transactionType =
                            TransactionType.EXPENSE;
                }

                /*
                 * 거래처
                 */
                String merchant =
                        getString(row.getCell(2));

                /*
                 * 금액
                 */
                Integer amount =
                        getInteger(amountCell);

                /*
                 * 거래내역 생성
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
                 * 키워드 기반 카테고리 분류
                 */
                Category category =
                        findCategory(
                                merchant,
                                keywords
                        );

                if (category != null) {

                    /*
                     * 키워드와 일치하는 카테고리가 있는 경우
                     */
                    transaction.setCategory(
                            category
                    );

                    transaction.setClassificationType(
                            ClassificationType.KEYWORD
                    );

                } else {

                    /*
                     * 카테고리를 찾지 못한 경우
                     *
                     * 이후 TransactionAiService에서
                     * LLM 분류 대상이 됨
                     */
                    transaction.setClassificationType(
                            ClassificationType.UNCONFIRMED
                    );
                }

                transactions.add(transaction);
            }
        }

        return transactions;
    }

    /**
     * 날짜 파싱
     */
    private LocalDateTime getDate(Cell cell) {

        if (cell == null
                || cell.getCellType() == CellType.BLANK) {

            return null;
        }

        /*
         * 문자열 날짜
         * 예: 2026.08.25 14:36:41
         */
        if (cell.getCellType() == CellType.STRING) {

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
         * Excel 날짜 형식
         */
        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {

            return cell.getLocalDateTimeCellValue();
        }

        throw new IllegalArgumentException(
                "날짜 형식이 올바르지 않습니다. value="
                        + cell.toString()
                        + ", cellType="
                        + cell.getCellType()
        );
    }

    /**
     * 금액 파싱
     */
    private Integer getInteger(Cell cell) {

        if (cell == null) {
            return null;
        }

        DataFormatter formatter =
                new DataFormatter();

        String value =
                formatter.formatCellValue(cell)
                        .trim();

        if (value.isEmpty()) {
            return null;
        }

        try {

            value = value.replace(",", "");

            return (int) Double.parseDouble(value);

        } catch (NumberFormatException e) {

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
    private String getString(Cell cell) {

        if (cell == null) {
            return null;
        }

        return cell.toString().trim();
    }

    /**
     * 거래처명을 기반으로 Category 검색
     */
    private Category findCategory(
            String merchant,
            List<CategoryKeyword> keywords
    ) {

        if (merchant == null
                || merchant.isBlank()) {

            return null;
        }

        for (CategoryKeyword categoryKeyword :
                keywords) {

            String keyword =
                    categoryKeyword.getKeyword();

            if (keyword != null
                    && merchant.contains(keyword)) {

                return categoryKeyword.getCategory();
            }
        }

        return null;
    }
}