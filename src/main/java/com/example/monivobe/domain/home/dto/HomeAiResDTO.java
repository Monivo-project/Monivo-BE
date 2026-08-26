package com.example.monivobe.domain.home.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HomeAiResDTO {

    @Builder
    public record ExpectedBudgetResult  (
            Integer expectedAmount,
            Integer recommendedBudget,
            String reason,
            Integer confidence
    ){}

}
