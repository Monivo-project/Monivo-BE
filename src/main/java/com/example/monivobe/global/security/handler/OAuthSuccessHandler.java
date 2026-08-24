package com.example.monivobe.global.security.handler;

import com.example.monivobe.global.security.entity.AuthMember;
import com.example.monivobe.global.security.entity.OAuthMember;
import com.example.monivobe.global.security.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import java.io.IOException;

@RequiredArgsConstructor
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        // 로그인한 사용자 가져오기
        OAuthMember member =
                (OAuthMember) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        // 우리 서비스 JWT 생성
        String accessToken = jwtUtil.createAccessToken(
                new AuthMember(member.getMember())
        );

        // Access Token을 HttpOnly Cookie에 저장
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", accessToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .domain("anna-lee.xyz")
                .path("/")
                .maxAge(60 * 60 * 24)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                accessTokenCookie.toString()
        );

        // 로그인 성공 후 프론트엔드로 이동
        response.sendRedirect("https://anna-lee.xyz");
    }
}