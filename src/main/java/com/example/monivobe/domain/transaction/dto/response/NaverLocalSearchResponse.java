package com.example.monivobe.domain.transaction.dto.response;

import lombok.Getter;
import java.util.List;
import lombok.Setter;

@Getter
@Setter
public class NaverLocalSearchResponse {

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {

        private String title;

        private String category;

        private String telephone;

        private String address;

        private String roadAddress;

        private String link;

        private String mapx;

        private String mapy;
    }
}