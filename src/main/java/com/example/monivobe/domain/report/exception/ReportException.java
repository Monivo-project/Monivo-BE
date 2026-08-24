package com.example.monivobe.domain.report.exception;


import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.exception.ProjectException;

public class ReportException extends ProjectException {
    public ReportException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
