package com.sk.skala.shopapi.event.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 선착순 이벤트 설정. (D23)
 *
 * @param strategy 수량을 지키는 방법. 네 가지 중 하나를 고른다
 */
@ConfigurationProperties(prefix = "shop.flash-sale")
public record FlashSaleProperties(FlashSaleStrategy strategy) {

    public FlashSaleProperties {
        if (strategy == null) {
            // 기본값을 코드에 두는 이유: 설정을 빠뜨렸을 때 기동이 실패하는 것보다
            // 가장 안전한 방식으로 도는 편이 낫다. ATOMIC_UPDATE는 외부 의존이 없고
            // 측정에서도 가장 나은 결과를 냈다.
            strategy = FlashSaleStrategy.ATOMIC_UPDATE;
        }
    }
}
