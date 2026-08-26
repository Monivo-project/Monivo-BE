package com.example.monivobe.domain.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 실제 가게 이름
     */
    @Column(nullable = false)
    private String name;

    /**
     * 검색용 정규화된 이름
     */
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    /**
     * 업종
     * 예: 음식점>한식
     */
    private String businessCategory;

    /**
     * Monivo 내부 업종
     * 예: 음식점
     */
    private String businessType;

    /**
     * 지번 주소
     */
    private String address;

    /**
     * 도로명 주소
     */
    private String roadAddress;

    /**
     * 전화번호
     */
    private String telephone;

    /**
     * 네이버 링크
     */
    @Column(length = 1000)
    private String naverLink;

    /**
     * 카카오 장소 ID
     */
    private String kakaoPlaceId;

    /**
     * 카카오 좌표
     */
    private Double latitude;
    private Double longitude;

    /**
     * 검색 출처
     * KAKAO
     * NAVER
     * BOTH
     */
    private String source;

    /**
     * 외부 검색 결과 신뢰도
     */
    private Double confidence;
}