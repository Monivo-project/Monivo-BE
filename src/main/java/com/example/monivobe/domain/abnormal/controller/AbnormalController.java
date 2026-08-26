package com.example.monivobe.domain.abnormal.controller;

import com.example.monivobe.domain.abnormal.dto.AbnormalResDTO;
import com.example.monivobe.domain.abnormal.service.AbnormalService;
import com.example.monivobe.domain.consumption.exception.code.ConsumptionSuccessCode;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.apiPayload.code.BaseSuccessCode;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/abnormal")
public class AbnormalController {

    private final AbnormalService abnormalService;

    @GetMapping
    public ApiResponse<List<AbnormalResDTO.AbnormalSpendingResDTO>> getAbnormal(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code,abnormalService.getAbnormal(authMember.getMember()));
    }

    @PatchMapping("/{transactionId}")
    public ApiResponse<Object> updateAbnormal(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code,abnormalService.updateAbnormal(transactionId, authMember.getMember()));
    }
}
