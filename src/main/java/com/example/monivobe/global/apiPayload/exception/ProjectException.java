package com.example.monivobe.global.apiPayload.exception;

import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProjectException extends RuntimeException {
   private final BaseErrorCode errorCode;
}
