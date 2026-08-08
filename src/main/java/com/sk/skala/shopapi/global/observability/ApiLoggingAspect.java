package com.sk.skala.shopapi.global.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sk.skala.shopapi.global.error.BusinessException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API 호출을 한 줄로 남기고 소요 시간을 잰다. (D28)
 *
 * <p>명세가 의존성으로 지정한 {@code spring-boot-starter-aop}를 실제로 쓰는 자리다.
 * 지금까지는 넣어만 두고 아무 데도 쓰지 않았다.
 *
 * <h2>왜 컨트롤러마다 로그를 넣지 않는가</h2>
 *
 * <p>같은 코드를 스물한 곳에 복사해야 하고, 새 엔드포인트를 추가할 때 빠뜨리기 쉽다.
 * 무엇보다 <b>업무 로직과 무관한 코드가 컨트롤러를 채운다.</b>
 * 이런 횡단 관심사가 AOP를 쓰는 이유다.
 *
 * <h2>예외를 삼키지 않는다</h2>
 *
 * <p>{@code catch}에서 로그만 남기고 다시 던진다. 여기서 삼키면 전역 예외 처리기가
 * 받지 못해 <b>클라이언트가 200을 받는다.</b> AOP로 감싸다가 흔히 저지르는 실수다.
 *
 * <h2>업무 예외와 서버 오류를 구분한다</h2>
 *
 * <p>잔액 부족(409)은 정상 흐름이고 NPE(500)는 결함이다. 둘을 같은 등급으로 남기면
 * 로그가 경고로 가득 차 진짜 문제가 묻힌다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiLoggingAspect {

    private final MeterRegistry meterRegistry;

    /** {@code @RestController}가 붙은 클래스의 모든 공개 메서드. */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    private void restController() {
    }

    @Around("restController()")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        String endpoint = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String httpCall = httpCall();
        String outcome = "success";

        try {
            Object result = joinPoint.proceed();
            log.info("{} {} ({}ms)", httpCall, endpoint, elapsedMs(start));
            return result;

        } catch (BusinessException e) {
            // 업무 규칙 위반은 정상 흐름이다. 잔액 부족과 품절은 결함이 아니다.
            outcome = e.getErrorCode().name();
            log.info("{} {} → {} ({}ms)", httpCall, endpoint, outcome, elapsedMs(start));
            throw e;

        } catch (Throwable e) {
            // 여기가 진짜 문제다. 스택 트레이스를 남긴다.
            outcome = "error";
            log.error("{} {} 실패 ({}ms)", httpCall, endpoint, elapsedMs(start), e);
            throw e;

        } finally {
            // 로그는 사람이 읽고 메트릭은 그래프가 읽는다. 둘 다 필요하다.
            // 로그만 있으면 "느려졌다"를 눈으로 세어야 하고,
            // 메트릭만 있으면 "왜 느려졌는지"를 알 수 없다.
            Timer.builder("shop.api.duration")
                    .tag("endpoint", endpoint)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 현재 요청의 메서드와 경로.
     *
     * <p>요청 컨텍스트가 없을 수도 있다(테스트에서 서비스를 직접 호출하는 경우).
     * 그때 예외가 나면 <b>로깅이 업무 흐름을 깨뜨린다.</b> 관측 코드가
     * 관측 대상을 망가뜨리는 것은 최악이라 조용히 물러난다.
     */
    private String httpCall() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "(HTTP 외부)";
    }
}
