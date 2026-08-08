package com.sk.skala.shopapi.event.app;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 전략 구현체를 종류별로 찾아주는 등록소. (D23)
 *
 * <p>스프링이 {@link FlashSaleClaimStrategy} 구현체를 전부 주입해주면 종류를 키로 묶는다.
 * 새 전략을 추가할 때 이 클래스를 고칠 필요가 없다.
 *
 * <p>{@code Map<FlashSaleStrategy, ...>}를 직접 주입받지 않은 이유는, 스프링이 빈 이름을
 * 키로 쓰기 때문이다. 빈 이름은 클래스 이름에서 오므로 클래스명을 바꾸면 조용히 깨진다.
 * {@code type()}을 키로 삼으면 그 연결이 코드에 드러난다.
 */
@Component
public class FlashSaleStrategies {

    private final Map<FlashSaleStrategy, FlashSaleClaimStrategy> byType =
            new EnumMap<>(FlashSaleStrategy.class);

    public FlashSaleStrategies(List<FlashSaleClaimStrategy> strategies) {
        for (FlashSaleClaimStrategy strategy : strategies) {
            FlashSaleClaimStrategy previous = byType.put(strategy.type(), strategy);
            if (previous != null) {
                // 같은 종류를 두 구현체가 주장하면 어느 쪽이 쓰일지 알 수 없다.
                // 기동 시점에 실패시켜 런타임에 헷갈리지 않게 한다.
                throw new IllegalStateException(
                        "전략 %s의 구현체가 둘 이상입니다".formatted(strategy.type()));
            }
        }
    }

    public FlashSaleClaimStrategy get(FlashSaleStrategy type) {
        FlashSaleClaimStrategy strategy = byType.get(type);
        if (strategy == null) {
            throw new IllegalStateException("구현되지 않은 전략입니다: " + type);
        }
        return strategy;
    }
}
