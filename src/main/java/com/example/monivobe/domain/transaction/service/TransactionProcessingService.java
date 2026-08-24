package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.domain.transaction.entity.Category;
import com.example.monivobe.domain.transaction.entity.CategoryKeyword;
import com.example.monivobe.domain.transaction.entity.Transaction;
import com.example.monivobe.domain.transaction.enums.ClassificationType;
import com.example.monivobe.domain.transaction.repository.CategoryKeywordRepository;
import com.example.monivobe.domain.transaction.repository.CategoryRepository;
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
    private final CategoryRepository categoryRepository;
    private final TransactionAiService transactionAiService;
    private final FileStorageService fileStorageService;

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

            // 3. Excel 파싱
            List<Transaction> transactions =
                    parseExcel(inputStream, member);

            if (transactions.isEmpty()) {
                return;
            }

            // 4. 먼저 DB 저장
            transactionRepository.saveAll(transactions);

            // 5. 미분류 거래만 추출
            List<Transaction> unclassifiedTransactions =
                    transactions.stream()
                            .filter(transaction ->
                                    transaction.getClassificationType()
                                            == ClassificationType.UNCONFIRMED
                            )
                            .toList();

            // 6. 미분류 거래만 LLM 분류
            transactionAiService.classifyUnclassifiedTransactions(
                    unclassifiedTransactions
            );

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
            Member member
    ) throws IOException {

        List<Transaction> transactions =
                new ArrayList<>();

        try (Workbook workbook =
                     WorkbookFactory.create(inputStream)) {

            Sheet sheet =
                    workbook.getSheetAt(0);

            /*
             * 기존 코드와 동일하게
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
                 * 기존 코드와 동일하게
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
                                date
                        );

                /*
                 * 키워드 기반 카테고리 분류
                 */
                Category category =
                        findCategory(merchant);

                if (category != null) {

                    transaction.setCategory(category);

                    transaction.setClassificationType(
                            ClassificationType.KEYWORD
                    );

                } else {

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
         * Excel 셀이 문자열인 경우
         *
         * 예:
         * 2026-08-24 15:30:00
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
                            "yyyy-MM-dd HH:mm:ss"
                    )
            );
        }

        /*
         * Excel 날짜 형식인 경우
         */
        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {

            return cell.getLocalDateTimeCellValue();
        }

        throw new IllegalArgumentException(
                "날짜 형식이 올바르지 않습니다. cellType="
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
     * 거래처명을 기반으로
     * CategoryKeyword를 검색
     */
    private Category findCategory(
            String merchant
    ) {

        if (merchant == null
                || merchant.isBlank()) {

            return null;
        }

        List<CategoryKeyword> keywords =
                categoryKeywordRepository.findAll();

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