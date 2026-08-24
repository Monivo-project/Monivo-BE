package com.example.monivobe.domain.home.controller;

import com.example.monivobe.domain.home.dto.HomeResDTO;
import com.example.monivobe.domain.home.exception.code.HomeSuccessCode;
import com.example.monivobe.domain.home.service.HomeService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;


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


    // 이번 주 일별 지출
    // GET /api/home/weekly?date=2026-08-25
    @GetMapping("/weekly")
    public ApiResponse<HomeResDTO.WeeklyExpense> getWeeklyExpense(
            @RequestParam LocalDate date,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        return ApiResponse.onSuccess(
                HomeSuccessCode.HOME_GET_SUCCESS,
                homeService.getWeeklyExpense(
                        date,
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
}