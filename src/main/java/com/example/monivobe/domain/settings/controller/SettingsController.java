package com.example.monivobe.domain.settings.controller;

import com.example.monivobe.domain.settings.dto.SettingsResDTO;
import com.example.monivobe.domain.settings.exception.code.SettingsSuccessCode;
import com.example.monivobe.domain.settings.service.SettingsService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    // 정기결제 후보 조회
    @GetMapping("/candidates")
    public ApiResponse<Object> getCandidates(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                settingsService.getCandidates(authMember.getMember()));
    }

    // 후보의 결제내역 조회
    @GetMapping("/candidates/{candidateId}/transactions")
    public ApiResponse<Object> getCandidatesDetail(
            @PathVariable Long candidateId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                settingsService.getCandidatesDetail(candidateId, authMember.getMember()));
    }

    // 정기결제로 등록
    @PatchMapping("/candidates/{candidateId}")
    public ApiResponse<Object> createCandidates(
            @PathVariable Long candidateId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                settingsService.createCandidates(candidateId, authMember.getMember()));
    }

    // 정기결제 아님 등록
    @PatchMapping("/candidates/{candidateId}/dismiss")
    public ApiResponse<Object> createCandidatesDismiss(
            @PathVariable Long candidateId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                settingsService.createCandidatesDismiss(candidateId, authMember.getMember()));
    }

    // 정기결제 등록 것
    @GetMapping("/subscriptions")
    public ApiResponse<SettingsResDTO.GetCandidates> getSubscriptions(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                settingsService.getSubscriptions(authMember.getMember()));
    }


    // 정기결제 미등록 조회
    @GetMapping("/subscriptions/dismissed")
    public ApiResponse<SettingsResDTO.GetCandidates> getSubscriptionsDismissed(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                settingsService.getSubscriptionsDismissed(authMember.getMember()));
    }




}
