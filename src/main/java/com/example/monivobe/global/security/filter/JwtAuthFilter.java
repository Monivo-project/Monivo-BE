package com.example.monivobe.global.security.filter;

import com.example.monivobe.domain.member.enums.SocialType;
import com.example.monivobe.global.apiPayload.ApiResponse;
import com.example.monivobe.global.apiPayload.code.BaseErrorCode;
import com.example.monivobe.global.apiPayload.code.GeneralErrorCode;
import com.example.monivobe.global.security.service.CustomUserDetailsService;
import com.example.monivobe.global.security.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 1. JWT 가져오기
            String token = resolveToken(request);

            // 2. 토큰이 없으면 다음 필터로
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            // 3. JWT 검증
            if (jwtUtil.isValid(token)) {

                // 4. JWT에서 사용자 정보 추출
                String uid = jwtUtil.getUid(token);
                SocialType socialType = jwtUtil.getSocialType(token);

                // 5. 회원 조회
                UserDetails member =
                        customUserDetailsService.loadUserByUsername(
                                socialType,
                                uid
                        );

                // 6. 인증 객체 생성
                Authentication auth =
                        new UsernamePasswordAuthenticationToken(
                                member,
                                null,
                                member.getAuthorities()
                        );

                // 7. SecurityContext에 인증 저장
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(auth);
            }

            // 8. 다음 필터
            filterChain.doFilter(request, response);

        } catch (Exception e) {

            ObjectMapper mapper = new ObjectMapper();
            BaseErrorCode code = GeneralErrorCode.UNAUTHORIZED;

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(code.getStatus().value());

            ApiResponse<Void> errorResponse =
                    ApiResponse.onFailure(code, null);

            mapper.writeValue(
                    response.getOutputStream(),
                    errorResponse
            );
        }
    }

    /**
     * Authorization Header 또는 Cookie에서 Access Token 추출
     */
    private String resolveToken(HttpServletRequest request) {

        // 1. Authorization Header 확인
        String authorization = request.getHeader("Authorization");

        if (authorization != null &&
                authorization.startsWith("Bearer ")) {

            return authorization.substring(7);
        }

        // 2. accessToken Cookie 확인
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {

            for (Cookie cookie : cookies) {

                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}