package com.example.monivobe.domain.transaction.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MerchantSearchResult {

    private String name;

    private String businessType;

    private String businessCategory;

    private String address;

    private String roadAddress;

    private String link;

    private String source;

    private Double confidence;
}