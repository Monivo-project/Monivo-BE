package com.example.monivobe.domain.member.exception.code;

import com.example.monivobe.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {

    MEMBER_SET_SUCCESS(HttpStatus.OK, "MEMBER200_1", "성공적으로 유저를 조회했습니다."),
    MEMBER_DUPLICATE_SUCCESS(HttpStatus.OK, "MEMBER200_1", "성공적으로 유저 중복 조회를 했습니다."),
    MEMBER_UPDATE_SUCCESS(HttpStatus.OK, "MEMBER200_2", "성공적으로 유저를 수정했습니다."),
    OK(HttpStatus.OK, "AUTH200_1", "로그인에 성공했습니다."),
    MEMBER_GET_SUCCESS(HttpStatus.OK, "MEMBER200_1", "성공적으로 유저 조회를 했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

}
