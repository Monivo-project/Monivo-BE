package com.example.monivobe.global.security.service;

import com.example.monivobe.domain.member.converter.MemberConverter;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.enums.SocialType;
import com.example.monivobe.domain.member.exception.MemberException;
import com.example.monivobe.domain.member.exception.code.MemberErrorCode;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.global.security.dto.KakaoDTO;
import com.example.monivobe.global.security.dto.OAuthDTO;
import com.example.monivobe.global.security.entity.OAuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuthService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(
            OAuth2UserRequest userRequest
    ) throws OAuth2AuthenticationException {

        // 카카오에서 사용자 정보 조회
        OAuth2User oAuthMember = super.loadUser(userRequest);

        SocialType providerId;
        String socialUid;

        Map<String, Object> attributes =
                oAuthMember.getAttribute("kakao_account");

        Map<String, Object> profile =
                (Map<String, Object>) attributes.get("profile");

        try {
            providerId = SocialType.valueOf(
                    userRequest
                            .getClientRegistration()
                            .getRegistrationId()
                            .toUpperCase()
            );

            socialUid = String.valueOf(
                    (Long) oAuthMember.getAttribute("id")
            );

        } catch (IllegalArgumentException e) {
            throw new MemberException(
                    MemberErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER
            );
        }

        // 카카오 사용자 정보
        OAuthDTO dto;

        // 실제 카카오 닉네임을 저장
        String kakaoName;

        switch (providerId) {
            case KAKAO -> {

                String email = attributes.get("email").toString();

                kakaoName = profile.get("nickname").toString();

                dto = new KakaoDTO(
                        socialUid,
                        email,
                        kakaoName
                );
            }

            default -> throw new MemberException(
                    MemberErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER
            );
        }

        boolean isNewMember = false;

        // 기존 회원 조회
        Member member =
                memberRepository
                        .findBySocialTypeAndSocialUid(
                                providerId,
                                socialUid
                        )
                        .orElse(null);

        // 신규 회원
        if (member == null) {

            Member newMember =
                    MemberConverter.toMember(
                            dto,
                            kakaoName
                    );

            memberRepository.save(newMember);

            member = newMember;
            isNewMember = true;
        }

        return new OAuthMember(
                member,
                oAuthMember.getAttributes(),
                isNewMember
        );
    }
}