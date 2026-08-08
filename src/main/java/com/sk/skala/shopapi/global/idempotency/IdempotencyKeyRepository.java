package com.sk.skala.shopapi.global.idempotency;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 멱등성 키 저장소. */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    /**
     * 아직 만료되지 않은 키를 찾는다.
     *
     * <p>만료 행을 지우는 배치가 없으므로 조회할 때 걸러야 한다.
     * `findById`만 쓰면 몇 달 전 키에도 옛 응답을 돌려주게 된다.
     */
    @Query("select k from IdempotencyKey k where k.key = :key and k.expiresAt > :now")
    Optional<IdempotencyKey> findUnexpired(@Param("key") String key, @Param("now") Instant now);
}
