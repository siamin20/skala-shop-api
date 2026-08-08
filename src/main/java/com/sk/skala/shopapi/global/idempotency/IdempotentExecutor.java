package com.sk.skala.shopapi.global.idempotency;

import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 멱등 실행의 바깥 계층.
 *
 * <p><b>트랜잭션 바깥에 있어야 한다.</b> 같은 키로 동시에 두 요청이 들어오면 나중 것이
 * 기본 키 위반으로 실패하는데, 그 예외를 트랜잭션 <i>안에서</i> 잡아봐야 이미 롤백 표시라
 * 아무 일도 할 수 없다. 낙관적 락 재시도를 트랜잭션 바깥에 두는 것과 같은 이유다.
 *
 * <p>동시 요청을 재시도로 흡수하지 않고 409로 돌려보낸다. 흡수하려면 먼저 커밋될 때까지
 * 기다렸다가 다시 읽어야 하는데, 그동안 커넥션을 붙잡고 있어야 한다.
 * 클라이언트가 잠시 뒤 다시 보내면 저장된 응답을 받으므로 결과는 같다.
 */
@Component
@RequiredArgsConstructor
public class IdempotentExecutor {

    private final IdempotencyStore store;

    /**
     * 멱등하게 실행한다.
     *
     * @throws BusinessException 같은 키의 요청이 처리 중이면 {@link ErrorCode#CONCURRENT_MODIFICATION}
     */
    public <T> T execute(String key, String customerId, Object request,
            Class<T> responseType, Supplier<T> operation) {

        try {
            return store.execute(key, customerId, request, responseType, operation);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "같은 키의 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요");
        }
    }
}
