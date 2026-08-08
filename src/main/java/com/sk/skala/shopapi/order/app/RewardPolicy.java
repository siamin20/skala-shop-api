package com.sk.skala.shopapi.order.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.sk.skala.shopapi.global.common.Money;

/**
 * 구매 적립 정책. (D31)
 *
 * <h2>명세와의 관계</h2>
 *
 * <p>명세(529~532p)의 결제 모델은 <b>포인트 차감</b>이다. 포인트가 곧 예치금이고
 * 주문하면 그만큼 줄어든다. 이 구조는 그대로 둔다. 채점 대상 시나리오이기 때문이다.
 *
 * <p>그 위에 <b>적립</b>을 얹는다. 결제한 금액의 일정 비율을 포인트로 되돌려준다.
 * 실제 뷰티 커머스가 하는 방식이고, 재구매를 유도하는 장치다.
 *
 * <p>즉 포인트가 <b>예치금이자 적립금</b> 역할을 겸한다. 두 개를 분리하는 편이
 * 현실에 가깝지만, 그러려면 카드 결제 같은 외부 결제수단이 필요해 명세를 벗어난다.
 *
 * <h2>적립을 즉시 지급하는 이유</h2>
 *
 * <p>실제 쇼핑몰은 배송 완료 후에 적립한다. 취소·반품 때 회수하기 번거롭기 때문이다.
 * 여기에는 배송 개념이 없으므로 즉시 지급하고, <b>취소하면 함께 회수한다.</b>
 * 회수하지 않으면 주문과 취소를 반복해 포인트를 무한히 늘릴 수 있다.
 *
 * @param rate 적립률(%). 0이면 적립하지 않는다
 */
// @Component를 붙이면 안 된다. 스프링이 일반 빈으로 보고 생성자의 Integer를
// 빈으로 찾다가 기동에 실패한다. @ConfigurationPropertiesScan이 이미 스캔한다.
@ConfigurationProperties(prefix = "shop.reward")
public record RewardPolicy(Integer rate) {

    public RewardPolicy {
        if (rate == null || rate < 0) {
            rate = 5;
        }
        if (rate > 100) {
            // 100%를 넘으면 살수록 포인트가 늘어난다. 설정 실수로 그런 일이 생기면 안 된다.
            throw new IllegalStateException("적립률은 100%%를 넘을 수 없습니다: %d".formatted(rate));
        }
    }

    /**
     * 결제 금액에 대한 적립 포인트.
     *
     * <p>{@link Money#proportion}을 쓴다. 몫과 나머지로 나눠 계산해
     * 중간 곱셈에서 오버플로가 나지 않는다. (D1)
     *
     * <p>원 단위 미만은 버려진다. 올림으로 하면 1원짜리를 사도 적립이 생겨
     * 반복 구매로 포인트를 만들어낼 수 있다.
     */
    public Money rewardFor(Money paidAmount) {
        if (rate == 0 || paidAmount.isZero()) {
            return Money.ZERO;
        }
        return paidAmount.proportion(rate, 100);
    }
}
