package com.example.monivobe.domain.transaction.service;

import com.example.monivobe.domain.transaction.dto.response.KakaoPlaceResponse;
import com.example.monivobe.domain.transaction.dto.response.NaverLocalSearchResponse;
import com.example.monivobe.domain.transaction.entity.Merchant;
import com.example.monivobe.domain.transaction.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;

    private final RestClient restClient =
            RestClient.builder().build();

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    @Value("${naver.api.client-id}")
    private String naverClientId;

    @Value("${naver.api.client-secret}")
    private String naverClientSecret;


    // ============================================================
    // Merchant 조회 또는 생성
    // ============================================================

    /**
     * 거래처명으로 Merchant를 조회하거나
     *
     * 1. DB 조회
     * 2. Kakao 검색
     * 3. Naver 검색
     * 4. 검색 결과 비교
     * 5. Merchant 생성
     * 6. DB 저장
     *
     * 순서로 처리한다.
     */
    public Optional<Merchant> findOrCreateMerchant(
            String merchantName
    ) {

        if (merchantName == null
                || merchantName.isBlank()) {

            log.warn(
                    "[MERCHANT] 거래처명이 비어 있습니다."
            );

            return Optional.empty();
        }

        log.info("========================================");
        log.info(
                "[MERCHANT] Merchant 검색 시작 - merchantName={}",
                merchantName
        );
        log.info("========================================");


        // ========================================================
        // 1. 거래처명 정규화
        // ========================================================

        String normalizedName =
                normalizeMerchant(merchantName);

        log.info(
                "[MERCHANT] normalizedName={}",
                normalizedName
        );


        // ========================================================
        // 2. DB 먼저 확인
        // ========================================================

        Optional<Merchant> existing =
                merchantRepository
                        .findByNormalizedName(
                                normalizedName
                        );

        if (existing.isPresent()) {

            Merchant merchant =
                    existing.get();

            log.info(
                    "[MERCHANT] DB에서 Merchant 발견 - id={}, name={}, type={}, category={}",
                    merchant.getId(),
                    merchant.getName(),
                    merchant.getBusinessType(),
                    merchant.getBusinessCategory()
            );

            return existing;
        }

        log.info(
                "[MERCHANT] DB에 Merchant가 없습니다. 외부 API 검색을 시작합니다."
        );


        // ========================================================
        // 3. Kakao 검색
        // ========================================================

        KakaoPlaceResponse kakaoResponse =
                searchKakao(merchantName);


        // ========================================================
        // 4. Naver 검색
        // ========================================================

        NaverLocalSearchResponse naverResponse =
                searchNaver(merchantName);


        // ========================================================
        // 5. 검색 결과 출력
        // ========================================================

        logKakaoResult(
                merchantName,
                kakaoResponse
        );

        logNaverResult(
                merchantName,
                naverResponse
        );


        // ========================================================
        // 6. Kakao + Naver 결과 비교
        // ========================================================

        Merchant merchant =
                matchResults(
                        merchantName,
                        kakaoResponse,
                        naverResponse
                );


        // ========================================================
        // 7. Merchant 생성 실패
        // ========================================================

        if (merchant == null) {

            log.warn(
                    "[MERCHANT] Merchant 매칭 실패 - merchantName={}",
                    merchantName
            );

            return Optional.empty();
        }


        // ========================================================
        // 8. normalizedName 설정
        // ========================================================

        merchant.setNormalizedName(
                normalizedName
        );


        // ========================================================
        // 9. DB 저장
        // ========================================================

        Merchant savedMerchant =
                merchantRepository.save(
                        merchant
                );

        log.info("========================================");
        log.info(
                "[MERCHANT] Merchant 저장 성공"
        );
        log.info(
                "[MERCHANT] id={}",
                savedMerchant.getId()
        );
        log.info(
                "[MERCHANT] name={}",
                savedMerchant.getName()
        );
        log.info(
                "[MERCHANT] businessType={}",
                savedMerchant.getBusinessType()
        );
        log.info(
                "[MERCHANT] businessCategory={}",
                savedMerchant.getBusinessCategory()
        );
        log.info(
                "[MERCHANT] source={}",
                savedMerchant.getSource()
        );
        log.info(
                "[MERCHANT] confidence={}",
                savedMerchant.getConfidence()
        );
        log.info("========================================");

        return Optional.of(savedMerchant);
    }


    // ============================================================
    // Kakao API
    // ============================================================

    /**
     * Kakao 장소 검색
     */
    private KakaoPlaceResponse searchKakao(
            String merchantName
    ) {

        log.info(
                "[KAKAO] 장소 검색 시작 - query={}",
                merchantName
        );

        try {

            KakaoPlaceResponse response =
                    restClient.get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host("dapi.kakao.com")
                                            .path(
                                                    "/v2/local/search/keyword.json"
                                            )
                                            .queryParam(
                                                    "query",
                                                    merchantName
                                            )
                                            .queryParam(
                                                    "size",
                                                    5
                                            )
                                            .build()
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "KakaoAK " + kakaoApiKey
                            )
                            .retrieve()
                            .body(
                                    KakaoPlaceResponse.class
                            );

            if (response == null) {

                log.warn(
                        "[KAKAO] response가 NULL입니다."
                );

                return null;
            }

            if (response.getDocuments() == null) {

                log.warn(
                        "[KAKAO] documents가 NULL입니다."
                );

                return response;
            }

            log.info(
                    "[KAKAO] 검색 성공 - resultCount={}",
                    response.getDocuments().size()
            );

            return response;

        } catch (RestClientException e) {

            log.error(
                    "[KAKAO] API 호출 실패 - query={}",
                    merchantName,
                    e
            );

            return null;
        }
    }


    // ============================================================
    // Naver API
    // ============================================================

    /**
     * Naver 지역 검색
     */
    private NaverLocalSearchResponse searchNaver(
            String merchantName
    ) {

        log.info(
                "[NAVER] 지역 검색 시작 - query={}",
                merchantName
        );

        try {

            NaverLocalSearchResponse response =
                    restClient.get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host("openapi.naver.com")
                                            .path(
                                                    "/v1/search/local.json"
                                            )
                                            .queryParam(
                                                    "query",
                                                    merchantName
                                            )
                                            .queryParam(
                                                    "display",
                                                    5
                                            )
                                            .build()
                            )
                            .header(
                                    "X-Naver-Client-Id",
                                    naverClientId
                            )
                            .header(
                                    "X-Naver-Client-Secret",
                                    naverClientSecret
                            )
                            .retrieve()
                            .body(
                                    NaverLocalSearchResponse.class
                            );

            if (response == null) {

                log.warn(
                        "[NAVER] response가 NULL입니다."
                );

                return null;
            }

            if (response.getItems() == null) {

                log.warn(
                        "[NAVER] items가 NULL입니다."
                );

                return response;
            }

            log.info(
                    "[NAVER] 검색 성공 - resultCount={}",
                    response.getItems().size()
            );

            return response;

        } catch (RestClientException e) {

            log.error(
                    "[NAVER] API 호출 실패 - query={}",
                    merchantName,
                    e
            );

            return null;
        }
    }


    // ============================================================
    // Kakao 검색 결과 로그
    // ============================================================

    private void logKakaoResult(
            String merchantName,
            KakaoPlaceResponse response
    ) {

        log.info(
                "---------- [KAKAO RESULT] ----------"
        );

        if (response == null) {

            log.warn(
                    "[KAKAO] {} 검색 결과 없음",
                    merchantName
            );

            return;
        }

        if (response.getDocuments() == null
                || response.getDocuments().isEmpty()) {

            log.warn(
                    "[KAKAO] {} 검색 결과 0건",
                    merchantName
            );

            return;
        }

        for (
                KakaoPlaceResponse.Document document
                : response.getDocuments()
        ) {

            log.info(
                    "[KAKAO] id={}, name={}, category={}, address={}, roadAddress={}, phone={}, x={}, y={}",
                    document.getId(),
                    document.getPlace_name(),
                    document.getCategory_name(),
                    document.getAddress_name(),
                    document.getRoad_address_name(),
                    document.getPhone(),
                    document.getX(),
                    document.getY()
            );
        }

        log.info(
                "------------------------------------"
        );
    }


    // ============================================================
    // Naver 검색 결과 로그
    // ============================================================

    private void logNaverResult(
            String merchantName,
            NaverLocalSearchResponse response
    ) {

        log.info(
                "---------- [NAVER RESULT] ----------"
        );

        if (response == null) {

            log.warn(
                    "[NAVER] {} 검색 결과 없음",
                    merchantName
            );

            return;
        }

        if (response.getItems() == null
                || response.getItems().isEmpty()) {

            log.warn(
                    "[NAVER] {} 검색 결과 0건",
                    merchantName
            );

            return;
        }

        for (
                NaverLocalSearchResponse.Item item
                : response.getItems()
        ) {

            log.info(
                    "[NAVER] title={}, category={}, address={}, roadAddress={}, telephone={}, link={}",
                    removeHtml(item.getTitle()),
                    item.getCategory(),
                    item.getAddress(),
                    item.getRoadAddress(),
                    item.getTelephone(),
                    item.getLink()
            );
        }

        log.info(
                "------------------------------------"
        );
    }


    // ============================================================
    // Kakao + Naver 매칭
    // ============================================================

    private Merchant matchResults(
            String merchantName,
            KakaoPlaceResponse kakaoResponse,
            NaverLocalSearchResponse naverResponse
    ) {

        boolean kakaoAvailable =
                kakaoResponse != null
                        && kakaoResponse.getDocuments() != null
                        && !kakaoResponse.getDocuments().isEmpty();

        boolean naverAvailable =
                naverResponse != null
                        && naverResponse.getItems() != null
                        && !naverResponse.getItems().isEmpty();


        log.info(
                "[MATCH] merchant={}, kakaoAvailable={}, naverAvailable={}",
                merchantName,
                kakaoAvailable,
                naverAvailable
        );


        // ========================================================
        // Kakao + Naver 모두 검색됨
        // ========================================================

        if (kakaoAvailable && naverAvailable) {

            for (
                    KakaoPlaceResponse.Document kakao
                    : kakaoResponse.getDocuments()
            ) {

                String kakaoName =
                        normalizeMerchant(
                                kakao.getPlace_name()
                        );

                for (
                        NaverLocalSearchResponse.Item naver
                        : naverResponse.getItems()
                ) {

                    String naverName =
                            normalizeMerchant(
                                    removeHtml(
                                            naver.getTitle()
                                    )
                            );

                    log.info(
                            "[MATCH] 비교 - kakaoName={}, naverName={}",
                            kakaoName,
                            naverName
                    );


                    // ====================================================
                    // 이름 비교
                    // ====================================================

                    boolean nameMatched =
                            kakaoName.equals(naverName)
                                    || kakaoName.contains(naverName)
                                    || naverName.contains(kakaoName);


                    if (!nameMatched) {

                        continue;
                    }


                    // ====================================================
                    // 주소 비교
                    // ====================================================

                    boolean addressMatched =
                            addressMatches(
                                    kakao,
                                    naver
                            );


                    log.info(
                            "[MATCH] nameMatched={}, addressMatched={}",
                            nameMatched,
                            addressMatched
                    );


                    /*
                     * 이름이 같고 주소까지 일치
                     */
                    if (addressMatched) {

                        log.info(
                                "[MATCH] Kakao + Naver 동일 매장 확인"
                        );

                        return buildFromBoth(
                                kakao,
                                naver
                        );
                    }


                    /*
                     * 이름은 동일하지만 주소가 없는 경우
                     *
                     * 이름이 정확하게 일치하면
                     * 낮은 신뢰도로 허용
                     */
                    if (kakaoName.equals(naverName)) {

                        log.info(
                                "[MATCH] 이름은 동일하지만 주소가 다릅니다. 이름 기반으로 낮은 신뢰도 저장"
                        );

                        return buildFromBothWithLowConfidence(
                                kakao,
                                naver
                        );
                    }
                }
            }


            log.warn(
                    "[MATCH] Kakao + Naver 검색 결과가 서로 일치하지 않습니다."
            );

            /*
             * 교차검증 실패 시
             * 각 API 단독 결과를 다시 검사
             */

            Merchant kakaoMerchant =
                    matchKakaoOnly(
                            merchantName,
                            kakaoResponse
                    );

            if (kakaoMerchant != null) {

                log.info(
                        "[MATCH] Kakao 단독 결과를 Merchant로 사용"
                );

                return kakaoMerchant;
            }

            Merchant naverMerchant =
                    matchNaverOnly(
                            merchantName,
                            naverResponse
                    );

            if (naverMerchant != null) {

                log.info(
                        "[MATCH] Naver 단독 결과를 Merchant로 사용"
                );

                return naverMerchant;
            }

            return null;
        }


        // ========================================================
        // Naver만 검색됨
        // ========================================================

        if (!kakaoAvailable && naverAvailable) {

            log.warn(
                    "[MATCH] Kakao 검색 실패. Naver 단독 결과를 검사합니다."
            );

            return matchNaverOnly(
                    merchantName,
                    naverResponse
            );
        }


        // ========================================================
        // Kakao만 검색됨
        // ========================================================

        if (kakaoAvailable && !naverAvailable) {

            log.warn(
                    "[MATCH] Naver 검색 실패. Kakao 단독 결과를 검사합니다."
            );

            return matchKakaoOnly(
                    merchantName,
                    kakaoResponse
            );
        }


        // ========================================================
        // 둘 다 실패
        // ========================================================

        log.warn(
                "[MATCH] Kakao와 Naver 모두 검색 결과가 없습니다."
        );

        return null;
    }


    // ============================================================
    // Kakao + Naver 공통 Merchant
    // ============================================================

    private Merchant buildFromBoth(
            KakaoPlaceResponse.Document kakao,
            NaverLocalSearchResponse.Item naver
    ) {

        return Merchant.builder()

                .name(
                        removeHtml(
                                naver.getTitle()
                        )
                )

                .businessCategory(
                        naver.getCategory()
                )

                .businessType(
                        extractBusinessType(
                                naver.getCategory()
                        )
                )

                .address(
                        naver.getAddress()
                )

                .roadAddress(
                        naver.getRoadAddress()
                )

                .telephone(
                        naver.getTelephone()
                )

                .naverLink(
                        naver.getLink()
                )

                .kakaoPlaceId(
                        kakao.getId()
                )

                .latitude(
                        parseDouble(
                                kakao.getY()
                        )
                )

                .longitude(
                        parseDouble(
                                kakao.getX()
                        )
                )

                .source("BOTH")

                .confidence(0.95)

                .build();
    }


    // ============================================================
    // 이름만 일치하는 경우
    // ============================================================

    private Merchant buildFromBothWithLowConfidence(
            KakaoPlaceResponse.Document kakao,
            NaverLocalSearchResponse.Item naver
    ) {

        return Merchant.builder()

                .name(
                        removeHtml(
                                naver.getTitle()
                        )
                )

                .businessCategory(
                        naver.getCategory()
                )

                .businessType(
                        extractBusinessType(
                                naver.getCategory()
                        )
                )

                .address(
                        naver.getAddress()
                )

                .roadAddress(
                        naver.getRoadAddress()
                )

                .telephone(
                        naver.getTelephone()
                )

                .naverLink(
                        naver.getLink()
                )

                .kakaoPlaceId(
                        kakao.getId()
                )

                .latitude(
                        parseDouble(
                                kakao.getY()
                        )
                )

                .longitude(
                        parseDouble(
                                kakao.getX()
                        )
                )

                .source("BOTH")

                .confidence(0.85)

                .build();
    }


    // ============================================================
    // Naver 단독
    // ============================================================

    private Merchant matchNaverOnly(
            String merchantName,
            NaverLocalSearchResponse response
    ) {

        if (response == null
                || response.getItems() == null
                || response.getItems().isEmpty()) {

            return null;
        }


        /*
         * 검색 결과 전체를 확인
         */
        for (
                NaverLocalSearchResponse.Item item
                : response.getItems()
        ) {

            String title =
                    removeHtml(
                            item.getTitle()
                    );

            String normalizedTitle =
                    normalizeMerchant(
                            title
                    );


            /*
             * 입력 거래처명과 정확히 일치
             */
            if (normalizeMerchant(merchantName)
                    .equals(normalizedTitle)) {

                log.info(
                        "[NAVER MATCH] 정확한 거래처 발견 - {}",
                        title
                );

                return Merchant.builder()

                        .name(title)

                        .normalizedName(
                                normalizedTitle
                        )

                        .businessCategory(
                                item.getCategory()
                        )

                        .businessType(
                                extractBusinessType(
                                        item.getCategory()
                                )
                        )

                        .address(
                                item.getAddress()
                        )

                        .roadAddress(
                                item.getRoadAddress()
                        )

                        .telephone(
                                item.getTelephone()
                        )

                        .naverLink(
                                item.getLink()
                        )

                        .source("NAVER")

                        .confidence(0.80)

                        .build();
            }
        }

        log.warn(
                "[NAVER MATCH] 정확히 일치하는 거래처가 없습니다. merchantName={}",
                merchantName
        );

        return null;
    }


    // ============================================================
    // Kakao 단독
    // ============================================================

    private Merchant matchKakaoOnly(
            String merchantName,
            KakaoPlaceResponse response
    ) {

        if (response == null
                || response.getDocuments() == null
                || response.getDocuments().isEmpty()) {

            return null;
        }


        for (
                KakaoPlaceResponse.Document item
                : response.getDocuments()
        ) {

            String placeName =
                    item.getPlace_name();

            String normalizedPlaceName =
                    normalizeMerchant(
                            placeName
                    );


            /*
             * 거래처명과 정확히 일치
             */
            if (normalizeMerchant(merchantName)
                    .equals(normalizedPlaceName)) {

                log.info(
                        "[KAKAO MATCH] 정확한 거래처 발견 - {}",
                        placeName
                );

                return Merchant.builder()

                        .name(
                                placeName
                        )

                        .normalizedName(
                                normalizedPlaceName
                        )

                        .businessCategory(
                                item.getCategory_name()
                        )

                        .businessType(
                                extractBusinessType(
                                        item.getCategory_name()
                                )
                        )

                        .address(
                                item.getAddress_name()
                        )

                        .roadAddress(
                                item.getRoad_address_name()
                        )

                        .telephone(
                                item.getPhone()
                        )

                        .kakaoPlaceId(
                                item.getId()
                        )

                        .latitude(
                                parseDouble(
                                        item.getY()
                                )
                        )

                        .longitude(
                                parseDouble(
                                        item.getX()
                                )
                        )

                        .source("KAKAO")

                        .confidence(0.80)

                        .build();
            }
        }

        log.warn(
                "[KAKAO MATCH] 정확히 일치하는 거래처가 없습니다. merchantName={}",
                merchantName
        );

        return null;
    }


    // ============================================================
    // 주소 비교
    // ============================================================

    private boolean addressMatches(
            KakaoPlaceResponse.Document kakao,
            NaverLocalSearchResponse.Item naver
    ) {

        String kakaoAddress =
                normalizeAddress(
                        kakao.getRoad_address_name()
                );

        if (kakaoAddress.isBlank()) {

            kakaoAddress =
                    normalizeAddress(
                            kakao.getAddress_name()
                    );
        }


        String naverAddress =
                normalizeAddress(
                        naver.getRoadAddress()
                );

        if (naverAddress.isBlank()) {

            naverAddress =
                    normalizeAddress(
                            naver.getAddress()
                    );
        }


        if (kakaoAddress.isBlank()
                || naverAddress.isBlank()) {

            log.warn(
                    "[ADDRESS] 주소가 비어 있어서 비교할 수 없습니다."
            );

            return false;
        }


        boolean matched =
                kakaoAddress.equals(naverAddress)
                        || kakaoAddress.contains(naverAddress)
                        || naverAddress.contains(kakaoAddress);


        log.info(
                "[ADDRESS] kakao={}, naver={}, matched={}",
                kakaoAddress,
                naverAddress,
                matched
        );

        return matched;
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
    // 주소 정규화
    // ============================================================

    private String normalizeAddress(
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
    // HTML 제거
    // ============================================================

    private String removeHtml(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("<[^>]*>", "")
                .trim();
    }


    // ============================================================
    // 좌표 변환
    // ============================================================

    private Double parseDouble(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        try {

            return Double.parseDouble(value);

        } catch (NumberFormatException e) {

            log.warn(
                    "[COORDINATE] 좌표 변환 실패 - value={}",
                    value
            );

            return null;
        }
    }


    // ============================================================
    // Business Type 추출
    // ============================================================

    /**
     * 예:
     *
     * 음식점>한식
     * 음식점>카페
     *
     * ->
     *
     * 음식점
     */
    private String extractBusinessType(
            String category
    ) {

        if (category == null
                || category.isBlank()) {

            return null;
        }

        String cleaned =
                category.trim();

        int index =
                cleaned.indexOf(">");

        if (index == -1) {

            return cleaned;
        }

        return cleaned
                .substring(
                        0,
                        index
                )
                .trim();
    }
}