package com.sk.skala.shopapi.global.observability;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 추적 아이디를 붙인다. (D28)
 *
 * <p>동시 요청이 섞이면 로그만 보고는 <b>어느 줄이 어느 요청의 것인지 알 수 없다.</b>
 * 특히 이 프로젝트는 동시성 테스트에서 스레드 수백 개가 같은 코드를 지나므로
 * 추적 아이디가 없으면 로그가 사실상 읽히지 않는다.
 *
 * <p>MDC에 넣으면 로그 패턴이 자동으로 찍어준다. 코드 곳곳에서 아이디를 들고 다니지
 * 않아도 된다. <b>다만 반드시 지워야 한다.</b> 톰캣은 스레드를 재사용하므로
 * 남겨두면 다음 요청이 남의 아이디를 달고 찍힌다. finally에서 지우는 이유다.
 *
 * <h2>필터 순서</h2>
 *
 * <p>{@code HIGHEST_PRECEDENCE}로 가장 앞에 둔다. 인증 필터보다 앞이어야
 * <b>인증에 실패한 요청도</b> 추적 아이디를 갖는다. 401이 왜 났는지 쫓아야 할 때
 * 그 로그에 아이디가 없으면 소용이 없다.
 *
 * <h2>클라이언트가 보낸 아이디를 이어받는다</h2>
 *
 * <p>프론트엔드나 게이트웨이가 이미 아이디를 붙였다면 그것을 쓴다.
 * 새로 만들면 같은 사용자 동작이 서비스 경계마다 다른 아이디로 쪼개진다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final String HEADER = "X-Trace-Id";

    /** UUID 전체는 로그 한 줄을 너무 차지한다. 앞 8자면 한 서비스 안에서 충분히 구분된다. */
    private static final int SHORT_LENGTH = 8;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String traceId = resolve(request);
        MDC.put(TRACE_ID, traceId);

        // 응답에도 실어 보낸다. 사용자가 "이 화면에서 오류가 났다"고 할 때
        // 개발자 도구의 이 헤더만 알려주면 서버 로그를 바로 찾을 수 있다.
        response.setHeader(HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 톰캣은 스레드를 재사용한다. 지우지 않으면 다음 요청이 남의 아이디를 단다.
            MDC.remove(TRACE_ID);
        }
    }

    private String resolve(HttpServletRequest request) {
        String given = request.getHeader(HEADER);
        if (given != null && !given.isBlank()) {
            // 클라이언트가 보낸 값이라 그대로 믿지 않는다. 로그를 오염시키거나
            // 줄바꿈을 넣어 위조 로그를 만드는 것을 막기 위해 길이와 문자를 제한한다.
            String sanitized = given.replaceAll("[^A-Za-z0-9-]", "");
            if (!sanitized.isBlank()) {
                return sanitized.substring(0, Math.min(sanitized.length(), 36));
            }
        }
        return UUID.randomUUID().toString().substring(0, SHORT_LENGTH);
    }
}
