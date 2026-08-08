package com.sk.skala.shopapi.event.queue;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 선착순 이벤트 가상 대기열. (D30)
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>지금까지의 방어는 <b>즉시 거절</b>이었다. 수량이 없으면 {@code SOLD_OUT},
 * 경합이 심하면 {@code LOCK_TIMEOUT}. 정확성은 지켜지지만 사용자 입장에서는
 * <b>왜 실패했는지 모른 채 버튼만 다시 누르게 된다.</b> 그 재시도가 부하를 다시 키운다.
 *
 * <p>대기열은 문제를 다른 방식으로 푼다. 거절하는 대신 <b>줄을 세우고 순번을 알려준다.</b>
 * 뒤에 선 사람은 기다리는 동안 서버를 두드리지 않는다.
 *
 * <h2>왜 Redis Sorted Set인가</h2>
 *
 * <p>대기열에 필요한 것은 두 가지다. <b>순서를 지키는 것</b>과 <b>내 순번을 아는 것</b>.
 *
 * <ul>
 *   <li>List는 순서는 지키지만 "내가 몇 번째인가"를 알려면 전체를 훑어야 한다
 *   <li>Sorted Set은 {@code ZRANK}로 순번을 O(log N)에 돌려준다
 * </ul>
 *
 * <p>점수(score)로 <b>들어온 시각</b>을 쓴다. 먼저 온 사람이 앞에 서는 것이 선착순의 정의다.
 *
 * <h2>DB가 아니라 Redis인 이유</h2>
 *
 * <p>대기열은 초당 수천 번 읽힌다. 기다리는 사람마다 자기 순번을 계속 확인하기 때문이다.
 * 그 부하를 DB로 보내면 <b>정작 주문을 처리해야 할 커넥션을 대기열 조회가 다 써버린다.</b>
 * 보호하려던 것을 대기열이 무너뜨리는 셈이다.
 *
 * <p>대기열 정보는 잃어도 된다는 점도 크다. Redis가 죽으면 줄이 사라지지만
 * 주문 데이터는 DB에 그대로 있다. 잃어도 되는 것을 잃어도 되는 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRoom {

    /**
     * 동시에 입장시킬 인원.
     *
     * <p>이 숫자가 대기열의 전부다. 크면 대기열이 무의미해지고, 작으면 처리량을 낭비한다.
     * 서버가 동시에 감당할 수 있는 주문 처리량에 맞춰야 한다.
     *
     * <p>D22 측정에서 재고 100개에 200 요청이 들어왔을 때 비관적 락이 모두 직렬화하며
     * 문제없이 처리했다. 그 절반인 100을 기준으로 잡는다. 시연에서는 설정으로 낮춘다.
     */
    private static final String ACTIVE_KEY = "flash-sale:%d:active";
    private static final String QUEUE_KEY = "flash-sale:%d:queue";

    /**
     * 대기표 수명.
     *
     * <p>창을 닫고 떠난 사람이 줄에 영원히 남으면 뒷사람이 앞으로 오지 못한다.
     * 이 시간 동안 순번 확인이 없으면 이탈로 보고 정리한다.
     */
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final WaitingRoomProperties properties;

    /**
     * 줄을 서고 대기표를 받는다.
     *
     * <p>이미 표가 있으면 새로 발급하지 않는다. 새로 발급하면 <b>새로고침할 때마다
     * 맨 뒤로 밀려</b> 영원히 입장하지 못한다.
     */
    public Ticket enter(Long flashSaleId, String customerId) {
        String queueKey = QUEUE_KEY.formatted(flashSaleId);
        String member = customerId;

        // score는 들어온 시각. 먼저 온 사람이 앞에 선다.
        // addIfAbsent가 아니라 add를 쓰면 새로고침마다 시각이 갱신돼 맨 뒤로 간다.
        Boolean added = redis.opsForZSet().addIfAbsent(queueKey, member, System.nanoTime());
        redis.expire(queueKey, TICKET_TTL);

        return position(flashSaleId, customerId, Boolean.TRUE.equals(added));
    }

    /** 지금 내 순번과 입장 가능 여부. */
    public Ticket position(Long flashSaleId, String customerId) {
        return position(flashSaleId, customerId, false);
    }

    private Ticket position(Long flashSaleId, String customerId, boolean justJoined) {
        String queueKey = QUEUE_KEY.formatted(flashSaleId);
        Long rank = redis.opsForZSet().rank(queueKey, customerId);

        if (rank == null) {
            // 줄에 없다. 이미 입장했거나 대기표가 만료됐다.
            return Ticket.admitted(UUID.randomUUID().toString(), 0);
        }

        Long total = redis.opsForZSet().zCard(queueKey);
        int ahead = rank.intValue();

        // rank는 0부터 시작한다. 입장 정원 안에 들면 통과.
        if (ahead < properties.admitCount()) {
            redis.opsForSet().add(ACTIVE_KEY.formatted(flashSaleId), customerId);
            redis.expire(ACTIVE_KEY.formatted(flashSaleId), TICKET_TTL);
            return Ticket.admitted(customerId, ahead);
        }

        return Ticket.waiting(customerId, ahead + 1, total == null ? 0 : total.intValue(), justJoined);
    }

    /**
     * 처리가 끝났으니 줄에서 빠진다.
     *
     * <p><b>이 호출이 없으면 대기열이 줄지 않는다.</b> 앞사람이 계속 자리를 차지해
     * 뒷사람이 영원히 기다린다. 성공했든 실패했든 반드시 불러야 한다.
     */
    public void leave(Long flashSaleId, String customerId) {
        redis.opsForZSet().remove(QUEUE_KEY.formatted(flashSaleId), customerId);
        redis.opsForSet().remove(ACTIVE_KEY.formatted(flashSaleId), customerId);
    }

    /** 입장 허가를 받은 상태인지. 대기열을 우회한 직접 호출을 막는다. */
    public boolean isAdmitted(Long flashSaleId, String customerId) {
        Set<String> active = redis.opsForSet().members(ACTIVE_KEY.formatted(flashSaleId));
        return active != null && active.contains(customerId);
    }

    /** 대기열 현황. 화면이 이 값을 그린다. */
    public record Ticket(
            String token,
            boolean admitted,
            int position,
            int total,
            boolean justJoined) {

        /** 대기열 없이 통과. 대기열을 끈 환경에서 쓴다. */
        public static Ticket pass(String token) {
            return new Ticket(token, true, 0, 0, false);
        }

        static Ticket admitted(String token, int position) {
            return new Ticket(token, true, position, 0, false);
        }

        static Ticket waiting(String token, int position, int total, boolean justJoined) {
            return new Ticket(token, false, position, total, justJoined);
        }

        /**
         * 예상 대기 시간(초).
         *
         * <p>한 사람 처리에 걸리는 시간을 곱하는 단순한 계산이다. 정확하지 않지만
         * <b>"얼마나 남았는지 전혀 모르는 것"보다는 낫다.</b> 사람은 남은 시간을 모를 때
         * 가장 빨리 이탈한다.
         */
        public int estimatedSeconds() {
            return admitted ? 0 : Math.max(1, position / 10);
        }
    }
}
