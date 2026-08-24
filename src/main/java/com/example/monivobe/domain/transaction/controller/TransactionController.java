package com.example.monivobe.domain.transaction.controller;

import com.example.monivobe.domain.transaction.exception.code.TransactionSuccessCode;
import com.example.monivobe.domain.transaction.service.TransactionService;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.apiPayload.code.BaseSuccessCode;
import com.example.monivobe.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthMember authMember
    ){
        BaseSuccessCode code = TransactionSuccessCode.FILE_UPLOAD_SUCCESS;
        return ApiResponse.onSuccess(code, transactionService.uploadFile(file, authMember.getMember()));
    }



}
