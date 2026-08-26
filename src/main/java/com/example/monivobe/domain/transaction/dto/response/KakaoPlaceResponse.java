package com.example.monivobe.domain.transaction.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KakaoPlaceResponse {

    private List<Document> documents;

    @Getter
    @Setter
    public static class Document {

        private String id;

        private String place_name;

        private String category_name;

        private String phone;

        private String address_name;

        private String road_address_name;

        private String place_url;

        private String x;

        private String y;
    }
}
