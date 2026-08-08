package com.sk.skala.shopapi.event.app;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Redis 원자 카운터 방식. DB 바깥에서 입장을 통제한다. (D23)
 *
 * <p>Redis는 명령을 <b>단일 스레드로 하나씩</b> 처리한다. 그래서 {@code DECRBY} 한 번이면
 * 읽기와 쓰기 사이에 다른 요청이 끼어들 수 없다. 락이라는 개념 자체가 필요 없다.
 *
 * <h2>DB를 함께 갱신하는 이유</h2>
 *
 * <p>Redis만 줄이면 <b>진실이 두 곳으로 갈라진다.</b> 관리자가 DB를 조회하면 여전히
 * 수량이 남아 있고, Redis가 재시작하면 카운터가 사라진다.
 *
 * <p>그래서 Redis가 입장을 통제한 뒤 DB에도 반영한다. Redis가 이미 인원을 걸러냈으므로
 * DB 갱신은 경합하지 않고, 조건부 UPDATE 한 문장이면 충분하다.
 *
 * <p><b>대신 Redis의 속도 이점이 줄어든다.</b> 결국 DB 왕복이 한 번 있기 때문이다.
 * 이 절충을 하지 않으려면 DB 반영을 나중에 몰아서 하는 write-behind가 필요한데,
 * 그건 이 과제 범위를 넘어선다. 하지 않았다.
 *
 * <h2>보상이 필요한 지점</h2>
 *
 * <p>{@code DECRBY} 결과가 음수면 이미 줄여버린 뒤다. 그대로 두면 <b>아무도 쓰지 못하는
 * 수량이 영구히 깎인다.</b> 곧바로 {@code INCRBY}로 되돌린다.
 *
 * <p>되돌리기 전에 프로세스가 죽으면 그 수량은 잃는다. 초과 판매(더 팔림)가 아니라
 * 과소 판매(덜 팔림) 방향이라 금전 손실은 없지만, <b>정확히 N개를 보장하지는 못한다.</b>
 * 이것이 DB 전략들과의 근본적인 차이다.
 */
@Component
@RequiredArgsConstructor
public class RedisFlashSaleStrategy implements FlashSaleClaimStrategy {

    /** 카운터 보관 기간. 이벤트가 끝나고도 남아 메모리를 차지하지 않게 한다. */
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final FlashSaleRepository repository;

    @Override
    public FlashSaleStrategy type() {
        return FlashSaleStrategy.REDIS;
    }

    /**
     * 카운터를 미리 채운다.
     *
     * <p>{@code setIfAbsent}를 쓴다. 서버가 여러 대일 때 각자 준비를 호출해도
     * <b>이미 팔린 카운터를 처음 값으로 되돌리지 않기</b> 위해서다.
     * 단순 {@code set}이었다면 재기동 한 번에 수량이 부활한다.
     */
    @Override
    public void prepare(Long flashSaleId, int totalQuantity) {
        redis.opsForValue().setIfAbsent(key(flashSaleId), String.valueOf(totalQuantity), TTL);
    }

    @Override
    public void claim(Long flashSaleId, int quantity) {
        Long remaining = redis.opsForValue().decrement(key(flashSaleId), quantity);

        if (remaining == null) {
            // 카운터가 없다. prepare를 부르지 않았거나 TTL이 지났다.
            // 여기서 DB 값을 읽어 즉석에서 채우면 이미 팔린 만큼이 부활하므로 하지 않는다.
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "이벤트 카운터가 준비되지 않았습니다: " + flashSaleId);
        }

        if (remaining < 0) {
            // 이미 줄여버린 뒤다. 되돌리지 않으면 아무도 쓰지 못하는 수량이 깎인 채 남는다.
            redis.opsForValue().increment(key(flashSaleId), quantity);
            throw new BusinessException(ErrorCode.SOLD_OUT, "선착순 수량이 모두 소진되었습니다");
        }

        // Redis가 이미 인원을 걸렀으므로 여기서는 경합하지 않는다.
        // 그래도 조건부 UPDATE를 쓴다. 조건이 없으면 Redis와 DB가 어긋났을 때
        // 음수 수량이 DB에 쓰이고 CHECK 제약에 걸려 원인을 알기 어려운 실패가 난다.
        if (repository.decreaseRemaining(flashSaleId, quantity) == 0) {
            redis.opsForValue().increment(key(flashSaleId), quantity);
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "이벤트 수량이 Redis와 DB에서 어긋났습니다: " + flashSaleId);
        }
    }

    @Override
    public void release(Long flashSaleId, int quantity) {
        if (repository.increaseRemaining(flashSaleId, quantity) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "이벤트 수량을 되돌릴 수 없습니다: " + flashSaleId);
        }
        redis.opsForValue().increment(key(flashSaleId), quantity);
    }

    private String key(Long flashSaleId) {
        return "flash-sale:%d:remaining".formatted(flashSaleId);
    }
}
