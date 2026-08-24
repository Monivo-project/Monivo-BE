package com.example.monivobe.domain.member.service;

import com.example.monivobe.domain.member.dto.MemberReqDTO;
import com.example.monivobe.domain.member.dto.MemberResDTO;
import com.example.monivobe.domain.member.entity.Member;
import com.example.monivobe.domain.member.exception.MemberException;
import com.example.monivobe.domain.member.exception.code.MemberErrorCode;
import com.example.monivobe.domain.member.repository.MemberRepository;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    // 닉네임 설정
    public String updateName(Long memberId, MemberReqDTO.nickname dto) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        if(memberRepository.existsByName(dto.nickname())){
            throw new MemberException(MemberErrorCode.MEMBER_NAME_DUPLICATE);
        }
        member.updateName(dto.nickname());
        return dto.nickname();
    }

    public String duplicateName(Long memberId, MemberReqDTO.nickname dto) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        if(memberRepository.existsByName(dto.nickname())){
            throw new MemberException(MemberErrorCode.MEMBER_NAME_DUPLICATE);
        }
        return "가입가능";
    }

    public String getName(Member member) {
        return member.getName();
    }


    public MemberResDTO.MemberResponse getMyInfo(
            UserDetails userDetails
    ) {

        if (!(userDetails instanceof AuthMember authMember)) {
            throw new MemberException(
                    MemberErrorCode.MEMBER_NOT_FOUND
            );
        }

        Member member = authMember.getMember();

        return MemberResDTO.MemberResponse.from(member);
    }
}
