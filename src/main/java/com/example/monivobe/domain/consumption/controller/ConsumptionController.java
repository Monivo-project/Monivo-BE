package com.example.monivobe.domain.consumption.controller;

import com.example.monivobe.domain.consumption.dto.ConsumptionResDTO;
import com.example.monivobe.domain.consumption.exception.code.ConsumptionSuccessCode;
import com.example.monivobe.domain.consumption.service.ConsumptionService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.apiPayload.code.BaseSuccessCode;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consumption")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    // 월별 소비 내역 조회
    // GET /api/consumption?year=2026&month=8
    @GetMapping
    public ApiResponse<ConsumptionResDTO.GetConsumption> getConsumption(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code, consumptionService.getConsumption(year, month, page, size, authMember.getMember()));
    }

    // 월별 카테고리 지출 조회
    // GET /api/consumption/category?year=2026&month=8
    @GetMapping("/category")
    public ApiResponse<ConsumptionResDTO.GetConsumptionCategory> getConsumptionCategory(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code, consumptionService.getConsumptionCategory(year, month, authMember.getMember()));
    }

    // 거래 검색
    // GET /api/consumption/search?year=2026&month=8&merchant=스타벅스&categoryId=5
    @GetMapping("/search")
    public ApiResponse<ConsumptionResDTO.GetConsumption> searchConsumption(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code, consumptionService.searchConsumption(year, month, merchant, categoryId, authMember.getMember()));
    }

    // 거래 상세 조회
    // GET /api/consumption/transaction/1
    @GetMapping("/transaction/{transactionId}")
    public ApiResponse<ConsumptionResDTO.GetConsumptionDetail> getConsumptionDetail(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code, consumptionService.getConsumptionDetail(transactionId, authMember.getMember()));
    }

    // 거래 삭제
    // DELETE /api/consumption/transaction/1
    @DeleteMapping("/transaction/{transactionId}")
    public ApiResponse<Object> deleteConsumptionDetail(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        consumptionService.deleteConsumptionDetail(transactionId, authMember.getMember());
        return ApiResponse.onSuccess(code, null);
    }

    // 카테고리 목록 조회
    // GET /api/consumption/categories
    @GetMapping("/categories")
    public ApiResponse<Object> getCategories(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        BaseSuccessCode code = ConsumptionSuccessCode.CONSUMPTION_GET_SUCCESS;
        return ApiResponse.onSuccess(code, consumptionService.getCategories(authMember.getMember()));
    }
}