package com.example.monivobe.domain.home.exception;


import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.exception.ProjectException;

public class HomeException extends ProjectException {
    public HomeException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
