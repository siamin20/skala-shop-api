package com.sk.skala.shopapi.global.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.skala.shopapi.global.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 시큐리티 단계에서 거부된 요청의 응답 형식.
 *
 * <p>인증·인가 실패는 <b>컨트롤러에 도달하기 전</b> 필터 체인에서 거부되므로
 * {@code @RestControllerAdvice}가 잡지 못한다. 그대로 두면 Spring Security 기본 응답이 나가고,
 * 다른 오류는 {@code ProblemDetail}인데 401·403만 형식이 달라진다.
 * 클라이언트는 두 가지 오류 형식을 다뤄야 한다.
 *
 * <p>그래서 여기서 직접 같은 형식으로 직렬화한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandlers {

    private static final String TYPE_PREFIX = "https://skala-shop/errors/";

    private final ObjectMapper objectMapper;

    /** 인증되지 않은 요청. 로그인이 필요하다. */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> write(response, ErrorCode.NOT_AUTHENTICATED, request);
    }

    /** 인증은 됐지만 권한이 없는 요청. */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> write(response, ErrorCode.ACCESS_DENIED, request);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode, HttpServletRequest request)
            throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(), errorCode.getMessage());
        problem.setTitle(errorCode.getMessage());
        problem.setType(URI.create(TYPE_PREFIX + errorCode.name().toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.name());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
