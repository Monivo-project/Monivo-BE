package com.example.monivobe.domain.settings.exception;


import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.exception.ProjectException;

public class SettingsException extends ProjectException {
    public SettingsException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
