package com.example.monivobe.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public class MemberReqDTO {

    // 닉네임 설정
    public record nickname(
            @NotBlank
            String nickname
    ){}

}
