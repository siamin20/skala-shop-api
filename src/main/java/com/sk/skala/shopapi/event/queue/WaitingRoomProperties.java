package com.sk.skala.shopapi.event.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대기열 설정. (D30)
 *
 * @param enabled     대기열 사용 여부. 끄면 예전처럼 즉시 처리한다
 * @param admitCount  동시에 입장시킬 인원. 서버가 감당할 주문 처리량에 맞춘다
 */
@ConfigurationProperties(prefix = "shop.waiting-room")
public record WaitingRoomProperties(Boolean enabled, Integer admitCount) {

    public WaitingRoomProperties {
        // 기본은 꺼둔다. Redis가 없는 환경에서도 애플리케이션이 그대로 동작해야 한다.
        // 명세 559p의 단독 컨테이너 실행(H2 내장, Redis 없음)이 그런 경우다.
        if (enabled == null) {
            enabled = false;
        }
        if (admitCount == null || admitCount < 1) {
            admitCount = 100;
        }
    }
}
