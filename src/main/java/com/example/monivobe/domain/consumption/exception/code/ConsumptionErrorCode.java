package com.example.monivobe.domain.consumption.exception.code;

import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ConsumptionErrorCode implements BaseErrorCode {

    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE404_1", "해당 코스를 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT404_1", "해당 댓글를 찾을 수 없습니다."),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMENT403_1", "해당 댓글에 접근할 권한이 없습니다."),
    COMMENT_ALREADY_DELETED(HttpStatus.NOT_FOUND, "COMMENT404_2", "이미 삭제된 댓글입니다."),
    QUERY_NOT_VALID(HttpStatus.NOT_FOUND, "QUERY404_1", "해당 쿼리을 찾을 수 없습니다."),
    LIKE_ALREADY_CREATED(HttpStatus.NOT_FOUND, "LIKE404_1", "이미 좋아요된 코스입니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE404_1", "해당 좋아요를 찾을 수 없습니다."),
    BOOKMARK_ALREADY_CREATED(HttpStatus.NOT_FOUND, "BOOKMARK404_1", "이미 북마크된 코스입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOKMARK404_1", "해당 북마크를 찾을 수 없습니다."),
    SORT_NOT_VALID(HttpStatus.NOT_FOUND, "SORT404_1", "해당 정렬을 찾을 수 없습니다."),
    LEVEL_NOT_VALID(HttpStatus.NOT_FOUND, "LEVEL404_1", "해당 레벨을 찾을 수 없습니다.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}
