package com.example.monivobe.domain.member.controller;

import com.example.monivobe.domain.member.dto.MemberReqDTO;
import com.example.monivobe.domain.member.dto.MemberResDTO;
import com.example.monivobe.domain.member.exception.code.MemberSuccessCode;
import com.example.monivobe.domain.member.service.MemberService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.apiPayload.code.BaseSuccessCode;
import com.example.monivobe.global.security.entity.AuthMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class MemberController {

    private final MemberService memberService;

    // 닉네임 설정
    @PatchMapping("/users/me/name")
    public ApiResponse<String> updateName(
            @RequestBody @Valid MemberReqDTO.nickname dto,
            @AuthenticationPrincipal AuthMember authMember
    ){
        BaseSuccessCode code = MemberSuccessCode.MEMBER_UPDATE_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.updateName(authMember.getMember().getId(), dto));
    }

    // 닉네임 설정
    @PostMapping("/users/me/name")
    public ApiResponse<String> duplicateName(
            @RequestBody @Valid MemberReqDTO.nickname dto,
            @AuthenticationPrincipal AuthMember authMember
    ){
        BaseSuccessCode code = MemberSuccessCode.MEMBER_UPDATE_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.duplicateName(authMember.getMember().getId(), dto));
    }

    // 사용자 이름
    @GetMapping("/users/me/name")
    public ApiResponse<MemberResDTO.GetMember> getName(
            @AuthenticationPrincipal AuthMember authMember
    ){
        BaseSuccessCode code = MemberSuccessCode.MEMBER_GET_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.getName(authMember.getMember()));
    }

    @GetMapping("/me")
    public ApiResponse<MemberResDTO.MemberResponse> getMyInfo(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        BaseSuccessCode code = MemberSuccessCode.MEMBER_GET_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.getMyInfo(userDetails));
    }
}
