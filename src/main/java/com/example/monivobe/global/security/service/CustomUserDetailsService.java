package com.example.monivobe.global.security.service;

import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.enums.SocialType;
import com.example.monivobe.domain.member.exception.MemberException;
import com.example.monivobe.domain.member.exception.code.MemberErrorCode;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService {
    private final MemberRepository memberRepository;

    public UserDetails loadUserByUsername(
            SocialType socialType,
            String username
    ) throws UsernameNotFoundException {
        // DB에서 기존 회원 정보 조회 & 인증 객체 생성
        Member member = memberRepository.findBySocialTypeAndSocialUid(socialType,username)
                .orElseThrow(()->new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        return new AuthMember(member);
    }
}
