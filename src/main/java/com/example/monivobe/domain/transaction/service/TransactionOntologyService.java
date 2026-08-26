package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.dto.response.TransactionResDTO;
import com.example.monivobe.domain.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionOntologyService {

    private static final String NS =
            "http://monivo.com/ontology#";

    private final Dataset ontologyDataset;

    /**
     * 여러 거래를 한 번에 Ontology에 저장
     */
    public void addTransactions(
            List<Transaction> transactions
    ) {

        if (transactions == null
                || transactions.isEmpty()) {

            return;
        }

        ontologyDataset.begin(
                ReadWrite.WRITE
        );

        try {

            Model model =
                    ontologyDataset.getDefaultModel();

            for (Transaction transaction : transactions) {

                addTransactionToModel(
                        model,
                        transaction
                );
            }

            ontologyDataset.commit();

        } catch (Exception e) {

            ontologyDataset.abort();

            throw new RuntimeException(
                    "거래내역 Ontology 저장 실패",
                    e
            );

        } finally {

            ontologyDataset.end();
        }
    }

    /**
     * 하나의 Transaction을 RDF로 변환
     */
    private void addTransactionToModel(
            Model model,
            Transaction transaction
    ) {

        if (transaction == null
                || transaction.getId() == null) {

            return;
        }

        /*
         * ========================================
         * Transaction
         * ========================================
         */

        String transactionUri =
                NS + "Transaction_" +
                        transaction.getId();

        Resource transactionResource =
                model.createResource(
                        transactionUri
                );

        /*
         * Transaction 타입
         */

        transactionResource.addProperty(
                RDF.type,
                model.createResource(
                        NS + "Transaction"
                )
        );

        /*
         * ========================================
         * Amount
         * ========================================
         */

        if (transaction.getAmount() != null) {

            transactionResource.addLiteral(
                    model.createProperty(
                            NS + "hasAmount"
                    ),
                    transaction.getAmount()
            );
        }

        /*
         * ========================================
         * Date
         * ========================================
         */

        if (transaction.getDate() != null) {

            transactionResource.addLiteral(
                    model.createProperty(
                            NS + "transactionDate"
                    ),
                    transaction.getDate().toString()
            );
        }

        /*
         * ========================================
         * Merchant
         * ========================================
         */

        if (transaction.getNormalizedMerchant() != null
                && !transaction
                .getNormalizedMerchant()
                .isBlank()) {

            String merchantName =
                    transaction
                            .getNormalizedMerchant()
                            .trim();

            String merchantUri =
                    NS + "Merchant_" +
                            normalizeUri(merchantName);

            Resource merchantResource =
                    model.createResource(
                            merchantUri
                    );

            /*
             * Merchant 타입
             */

            merchantResource.addProperty(
                    RDF.type,
                    model.createResource(
                            NS + "Merchant"
                    )
            );

            /*
             * Merchant 이름
             */

            merchantResource.addProperty(
                    model.createProperty(
                            NS + "merchantName"
                    ),
                    merchantName
            );

            /*
             * Transaction → Merchant
             */

            transactionResource.addProperty(
                    model.createProperty(
                            NS + "hasMerchant"
                    ),
                    merchantResource
            );
        }

        /*
         * ========================================
         * Original Merchant
         * ========================================
         */

        if (transaction.getMerchant() != null
                && !transaction
                .getMerchant()
                .isBlank()) {

            transactionResource.addProperty(
                    model.createProperty(
                            NS + "originalMerchant"
                    ),
                    transaction.getMerchant()
            );
        }

        /*
         * ========================================
         * Member
         * ========================================
         */

        if (transaction.getMember() != null
                && transaction
                .getMember()
                .getId() != null) {

            Long memberId =
                    transaction
                            .getMember()
                            .getId();

            Resource memberResource =
                    model.createResource(
                            NS + "Member_" +
                                    memberId
                    );

            /*
             * Member 타입
             */

            memberResource.addProperty(
                    RDF.type,
                    model.createResource(
                            NS + "Member"
                    )
            );

            /*
             * Transaction → Member
             */

            transactionResource.addProperty(
                    model.createProperty(
                            NS + "madeBy"
                    ),
                    memberResource
            );

            /*
             * Member → Transaction
             */

            memberResource.addProperty(
                    model.createProperty(
                            NS + "hasTransaction"
                    ),
                    transactionResource
            );
        }

        /*
         * ========================================
         * Category
         * ========================================
         */

        if (transaction.getCategory() != null
                && transaction
                .getCategory()
                .getId() != null) {

            Long categoryId =
                    transaction
                            .getCategory()
                            .getId();

            Resource categoryResource =
                    model.createResource(
                            NS + "Category_" +
                                    categoryId
                    );

            /*
             * Category 타입
             */

            categoryResource.addProperty(
                    RDF.type,
                    model.createResource(
                            NS + "Category"
                    )
            );

            /*
             * Transaction → Category
             */

            transactionResource.addProperty(
                    model.createProperty(
                            NS + "hasCategory"
                    ),
                    categoryResource
            );
        }

        /*
         * ========================================
         * Classification Type
         * ========================================
         */

        if (transaction.getClassificationType() != null) {

            transactionResource.addLiteral(
                    model.createProperty(
                            NS + "classificationType"
                    ),
                    transaction
                            .getClassificationType()
                            .name()
            );
        }

        /*
         * ========================================
         * Transaction Type
         * ========================================
         */

        if (transaction.getTransactionType() != null) {

            transactionResource.addLiteral(
                    model.createProperty(
                            NS + "transactionType"
                    ),
                    transaction
                            .getTransactionType()
                            .name()
            );
        }
    }

    /**
     * AI 분류 이후 Ontology 정보 업데이트
     */
    public void updateClassification(
            Transaction transaction
    ) {

        if (transaction == null
                || transaction.getId() == null) {

            return;
        }

        ontologyDataset.begin(
                ReadWrite.WRITE
        );

        try {

            Model model =
                    ontologyDataset.getDefaultModel();

            Resource transactionResource =
                    model.createResource(
                            NS + "Transaction_" +
                                    transaction.getId()
                    );

            /*
             * 기존 Category 삭제
             */

            Property hasCategory =
                    model.createProperty(
                            NS + "hasCategory"
                    );

            model.removeAll(
                    transactionResource,
                    hasCategory,
                    null
            );

            /*
             * 새로운 Category 추가
             */

            if (transaction.getCategory() != null
                    && transaction
                    .getCategory()
                    .getId() != null) {

                Resource categoryResource =
                        model.createResource(
                                NS + "Category_" +
                                        transaction
                                                .getCategory()
                                                .getId()
                        );

                categoryResource.addProperty(
                        RDF.type,
                        model.createResource(
                                NS + "Category"
                        )
                );

                transactionResource.addProperty(
                        hasCategory,
                        categoryResource
                );
            }

            /*
             * 기존 Classification 삭제
             */

            Property classificationType =
                    model.createProperty(
                            NS + "classificationType"
                    );

            model.removeAll(
                    transactionResource,
                    classificationType,
                    null
            );

            /*
             * 새로운 Classification 추가
             */

            if (transaction
                    .getClassificationType() != null) {

                transactionResource.addLiteral(
                        classificationType,
                        transaction
                                .getClassificationType()
                                .name()
                );
            }

            ontologyDataset.commit();

        } catch (Exception e) {

            ontologyDataset.abort();

            throw new RuntimeException(
                    "거래내역 Ontology 업데이트 실패",
                    e
            );

        } finally {

            ontologyDataset.end();
        }
    }

    /**
     * Transaction Ontology 삭제
     */
    public void deleteTransaction(
            Long transactionId
    ) {

        ontologyDataset.begin(
                ReadWrite.WRITE
        );

        try {

            Model model =
                    ontologyDataset.getDefaultModel();

            Resource transactionResource =
                    model.createResource(
                            NS + "Transaction_" +
                                    transactionId
                    );

            model.removeAll(
                    transactionResource,
                    null,
                    null
            );

            model.removeAll(
                    null,
                    null,
                    transactionResource
            );

            ontologyDataset.commit();

        } catch (Exception e) {

            ontologyDataset.abort();

            throw new RuntimeException(
                    "거래내역 Ontology 삭제 실패",
                    e
            );

        } finally {

            ontologyDataset.end();
        }
    }

    /**
     * URI에 사용할 수 있도록 문자열 정리
     */
    private String normalizeUri(
            String value
    ) {

        return value
                .replaceAll(
                        "[^a-zA-Z0-9가-힣]",
                        "_"
                );
    }



    public TransactionResDTO.TransactionOntologyContext getTransactionContext(
            Long transactionId
    ) {
        if (transactionId == null) {
            return null;
        }

        ontologyDataset.begin(ReadWrite.READ);

        try {
            Model model = ontologyDataset.getDefaultModel();

            Resource transactionResource =
                    model.createResource(
                            NS + "Transaction_" + transactionId
                    );

            if (!model.contains(
                    transactionResource,
                    RDF.type,
                    model.createResource(NS + "Transaction")
            )) {
                return null;
            }

            Property hasMerchant =
                    model.createProperty(NS + "hasMerchant");

            Property hasCategory =
                    model.createProperty(NS + "hasCategory");

            Resource merchantResource =
                    model
                            .listObjectsOfProperty(
                                    transactionResource,
                                    hasMerchant
                            )
                            .hasNext()
                            ? model
                            .listObjectsOfProperty(
                                    transactionResource,
                                    hasMerchant
                            )
                            .next()
                            .asResource()
                            : null;

            Resource categoryResource =
                    model
                            .listObjectsOfProperty(
                                    transactionResource,
                                    hasCategory
                            )
                            .hasNext()
                            ? model
                            .listObjectsOfProperty(
                                    transactionResource,
                                    hasCategory
                            )
                            .next()
                            .asResource()
                            : null;

            String merchantName =
                    getLabel(
                            model,
                            merchantResource
                    );

            String categoryName =
                    getLabel(
                            model,
                            categoryResource
                    );

            String parentCategoryName =
                    getParentCategory(
                            model,
                            categoryResource
                    );

            return new TransactionResDTO.TransactionOntologyContext(
                    transactionId,
                    merchantName,
                    categoryName,
                    parentCategoryName
            );

        } finally {
            ontologyDataset.end();
        }
    }

    private String getLabel(
            Model model,
            Resource resource
    ) {

        if (resource == null) {
            return null;
        }

        var statement =
                model.getProperty(
                        resource,
                        org.apache.jena.vocabulary.RDFS.label
                );

        if (statement == null) {
            return null;
        }

        return statement.getString();
    }

    private String getParentCategory(
            Model model,
            Resource categoryResource
    ) {

        if (categoryResource == null) {
            return null;
        }

        Property belongsTo =
                model.createProperty(
                        NS + "belongsTo"
                );

        var iterator =
                model.listObjectsOfProperty(
                        categoryResource,
                        belongsTo
                );

        if (!iterator.hasNext()) {
            return null;
        }

        Resource parent =
                iterator.next().asResource();

        return getLabel(
                model,
                parent
        );
    }
}