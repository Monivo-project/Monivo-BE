package com.example.monivobe.domain.report.controller;

import com.example.monivobe.domain.report.dto.ReportResDTO;
import com.example.monivobe.domain.report.exception.code.ReportSuccessCode;
import com.example.monivobe.domain.report.service.ReportService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;


    /**
     * 소비 분석 리포트 조회
     *
     * GET
     * /api/report?year=2026&month=8
     */
    @GetMapping
    public ApiResponse<ReportResDTO.Report> getReport(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @AuthenticationPrincipal AuthMember authMember
    ) {

        return ApiResponse.onSuccess(
                ReportSuccessCode.REPORT_GET_SUCCESS,
                reportService.getReport(
                        year,
                        month,
                        authMember.getMember()
                )
        );
    }
}
