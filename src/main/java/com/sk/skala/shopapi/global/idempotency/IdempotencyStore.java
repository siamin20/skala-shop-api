package com.sk.skala.shopapi.global.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 멱등 실행의 실제 처리.
 *
 * <p>작업과 키 기록을 <b>같은 트랜잭션</b>에 묶는다. 나뉘면 작업은 성공했는데 키 저장이 실패해
 * 재시도가 중복 실행되거나, 키만 남고 작업이 롤백돼 실행되지 않은 결과를 돌려주게 된다.
 *
 * <p>작업이 실패하면 키도 함께 롤백된다. 실패한 요청을 기억하면 재시도가 영원히 막힌다.
 *
 * <p>동시 요청 처리는 {@link IdempotentExecutor}가 맡는다. 여기서 기본 키 위반을 잡으려 해도
 * 트랜잭션이 이미 롤백 표시라 아무것도 할 수 없기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private final IdempotencyKeyRepository repository;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 키가 있으면 저장된 응답을, 없으면 작업을 실행하고 결과를 기록한다.
     *
     * @param key         클라이언트가 보낸 멱등성 키
     * @param customerId  요청자
     * @param request     요청 본문. 지문 계산에 쓴다
     * @param responseType 응답 타입. 저장된 JSON을 되돌릴 때 필요하다
     * @param operation   실제 작업
     */
    @Transactional
    public <T> T execute(String key, String customerId, Object request,
            Class<T> responseType, Supplier<T> operation) {

        String fingerprint = fingerprint(request);

        Optional<IdempotencyKey> saved = repository.findUnexpired(key, Instant.now());
        if (saved.isPresent()) {
            return replay(saved.get(), customerId, fingerprint, responseType);
        }

        T response = operation.get();

        repository.save(new IdempotencyKey(
                key, customerId, fingerprint, serialize(response),
                Instant.now().plus(properties.validity())));

        return response;
    }

    /**
     * 저장된 응답을 돌려준다. 작업은 실행하지 않는다.
     *
     * <p>키 소유자와 요청 내용을 함께 확인한다. 키만 맞으면 되게 두면
     * 남의 키로 그 응답을 훔쳐보거나, 같은 키에 다른 금액을 실어 보내
     * 실행되지 않은 채 성공 응답만 받아낼 수 있다.
     */
    private <T> T replay(IdempotencyKey saved, String customerId, String fingerprint,
            Class<T> responseType) {

        if (!saved.belongsTo(customerId)) {
            // 다른 사람의 키라는 사실 자체를 알려주지 않는다. 키 추측의 단서가 된다.
            throw new BusinessException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED, "이미 사용된 멱등성 키입니다");
        }

        if (!saved.matches(fingerprint)) {
            throw new BusinessException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "같은 키로 다른 요청을 보낼 수 없습니다. 새 키를 사용하세요");
        }

        return deserialize(saved.getResponseBody(), responseType);
    }

    /**
     * 요청 내용의 지문을 만든다.
     *
     * <p>요청 원문을 그대로 저장하지 않는 이유는 두 가지다. 길이가 요청마다 다르고,
     * 본문에 민감한 값이 들어오면 그대로 남는다. 해시는 길이가 고정이고 원문을 복원할 수 없다.
     */
    private String fingerprint(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("요청 지문을 만들 수 없습니다", e);
        }
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응답을 저장할 수 없습니다", e);
        }
    }

    private <T> T deserialize(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            // 응답 DTO 구조가 바뀌어 옛 기록을 되돌릴 수 없는 경우다.
            // 조용히 재실행하면 중복이 생기므로 명시적으로 실패시킨다.
            throw new IllegalStateException(
                    "저장된 응답을 되돌릴 수 없습니다. 응답 구조가 바뀌었을 수 있습니다", e);
        }
    }
}
