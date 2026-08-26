package com.example.monivobe.domain.home.controller;

import com.example.monivobe.domain.home.dto.HomeResDTO;
import com.example.monivobe.domain.home.exception.code.HomeSuccessCode;
import com.example.monivobe.domain.home.service.ExpectedBudgetService;
import com.example.monivobe.domain.home.service.HomeService;
import com.example.monivobe.domain.home.service.TestService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;
    private final ExpectedBudgetService expectedBudgetService;

    // 대시보드 상단 요약
    // GET /api/home?year=2026&month=8
    @GetMapping
    public ApiResponse<HomeResDTO.Summary> getSummary(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(
                HomeSuccessCode.HOME_GET_SUCCESS,
                homeService.getSummary(
                        year,
                        month,
                        authMember.getMember()
                )
        );
    }


    // 최근 거래
    // GET /api/home/recent-transactions
    @GetMapping("/recent-transactions")
    public ApiResponse<HomeResDTO.RecentTransactions> getRecentTransactions(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(
                HomeSuccessCode.HOME_GET_SUCCESS,
                homeService.getRecentTransactions(
                        authMember.getMember()
                )
        );
    }

    // 예상 지출
    // GET /api/home/expected-budget
    @GetMapping("/expected-budget")
    public ApiResponse<HomeResDTO.ExpectedBudget> getExpectedBudget(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return ApiResponse.onSuccess(
                HomeSuccessCode.HOME_GET_SUCCESS,
                expectedBudgetService.getExpectedBudget(
                        authMember.getMember(),
                        year,
                        month
                )
        );
    }

    // 최근 6개월 지출
    @GetMapping("/monthly-spending")
    public ApiResponse<Object> getMonthlySpending(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(HomeSuccessCode.HOME_GET_SUCCESS, homeService.getMonthlySpending(authMember.getMember()));
    }
}
