package com.example.monivobe.domain.member.dto;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.enums.SocialType;
import lombok.Builder;

public class MemberResDTO {

    @Builder
    public record Login(
            String accessToken
    ) {}

    @Builder
    public record MemberResponse(
            Long id,
            String email,
            String nickname,
            SocialType socialType
    ) {

        public static MemberResponse from(Member member) {
            return new MemberResponse(
                    member.getId(),
                    member.getEmail(),
                    member.getName(),
                    member.getSocialType()
            );
        }
    }
}