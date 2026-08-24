package com.example.monivobe.domain.home.exception.code;

import com.example.monivobe.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HomeSuccessCode implements BaseSuccessCode {

    FILE_UPLOAD_SUCCESS(HttpStatus.CREATED, "UPLOAD201_1", "성공적으로 파일을 저장했습니다."),
    HOME_GET_SUCCESS(HttpStatus.OK, "HOME200_1", "성공적으로 홈을 조회했습니다."),
    COMMENT_UPDATE_SUCCESS(HttpStatus.OK, "COMMENT200_2", "성공적으로 댓글을 수정했습니다."),
    COMMENT_DELETE_SUCCESS(HttpStatus.OK, "COMMENT200_3", "성공적으로 댓글을 삭제했습니다."),
    COMMENT_CREATE_SUCCESS(HttpStatus.CREATED, "COMMENT201_1", "성공적으로 댓글을 생성했습니다."),
    LIKE_DELETE_SUCCESS(HttpStatus.OK, "LIKE200_1", "성공적으로 좋아요를 삭제했습니다."),
    LIKE_CREATE_SUCCESS(HttpStatus.CREATED, "LIKE201_1", "성공적으로 좋아요를 생성했습니다."),
    BOOKMARK_DELETE_SUCCESS(HttpStatus.OK, "BOOKMARK200_1", "성공적으로 북마크를 삭제했습니다."),
    BOOKMARK_CREATE_SUCCESS(HttpStatus.CREATED, "BOOKMARK201_1", "성공적으로 북마크를 생성했습니다."),
    RANKING_GET_SUCCESS(HttpStatus.OK, "RANKING200_1", "성공적으로 랭킹을 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
