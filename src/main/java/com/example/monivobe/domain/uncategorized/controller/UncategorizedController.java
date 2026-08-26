package com.example.monivobe.domain.uncategorized.controller;

import com.example.monivobe.domain.settings.exception.code.SettingsSuccessCode;
import com.example.monivobe.domain.uncategorized.dto.UncategorizedResDTO;
import com.example.monivobe.domain.uncategorized.service.UncategorizedService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/uncategorized")
public class UncategorizedController {

    private final UncategorizedService uncategorizedService;

    // 미분류 목록 조회
    @GetMapping
    public ApiResponse<List<UncategorizedResDTO.GetUncategorized>> getUncategorized(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(SettingsSuccessCode.SETTINGS_SUCCESS_CODE,
                uncategorizedService.getUncategorized(authMember.getMember()));
    }
}
