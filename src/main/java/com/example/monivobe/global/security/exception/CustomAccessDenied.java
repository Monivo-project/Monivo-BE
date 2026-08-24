package com.example.monivobe.global.security.exception;

import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.code.GeneralErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

// 접근 권한
// 인증은 되었지만 권한이 없는 사용저가 요청했을 때 처리
public class CustomAccessDenied implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,     // 사용자의 요청 정보
            HttpServletResponse response,   // 클라이언트에게 보낼 응답 객체
            AccessDeniedException accessDeniedException     // 권한 예외 부족
    ) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        BaseErrorCode code = GeneralErrorCode.FORBIDDEN;

        // 응답 Content-Type, HTTP 상태코드 정의
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code.getStatus().value());

        // Response Body에 응답통일한 객체를 넣기
        ApiResponse<Void> errorResponse = ApiResponse.onFailure(code,null);

        // 실제 Response로 덮어쓰기
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
