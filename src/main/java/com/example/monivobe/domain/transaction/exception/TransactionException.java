package com.example.monivobe.domain.transaction.exception;


import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.exception.ProjectException;

public class TransactionException extends ProjectException {
    public TransactionException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
