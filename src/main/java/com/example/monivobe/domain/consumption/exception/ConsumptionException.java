package com.example.monivobe.domain.consumption.exception;


import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.exception.ProjectException;

public class ConsumptionException extends ProjectException {
    public ConsumptionException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
