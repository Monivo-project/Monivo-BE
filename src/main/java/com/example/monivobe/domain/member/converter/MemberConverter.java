package com.example.monivobe.domain.member.converter;

import com.example.monivobe.domain.member.dto.MemberResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.global.security.dto.OAuthDTO;

public class MemberConverter {

    public static Member toMember(OAuthDTO dto, String name) {
        return Member.builder()
                .name(name)
                .socialType(dto.getSocialType())
                .socialUid(dto.getSocialUid())
                .email(dto.getSocialEmail())
                .build();
    }

    public static MemberResDTO.Login toLogin(String accessToken) {
        return new MemberResDTO.Login(accessToken);
    }
}
