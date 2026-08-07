package com.sk.skala.shopapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * skala-shop-api 애플리케이션 진입점.
 *
 * <p>온라인 쇼핑몰 백엔드 REST API다. 상품·고객·주문을 관리하며,
 * 재고와 선착순 이벤트에서 발생하는 동시성 문제를 자원별로 다른 락 전략으로 처리한다.
 *
 * <p>{@code @SpringBootApplication}은 세 애노테이션을 합친 것이다.
 * <ul>
 *   <li>{@code @SpringBootConfiguration} — 이 클래스를 설정 클래스로 등록한다
 *   <li>{@code @EnableAutoConfiguration} — 클래스패스에 있는 라이브러리를 보고 설정을 자동 구성한다
 *   <li>{@code @ComponentScan} — 이 클래스가 속한 패키지 이하를 스캔해 빈을 등록한다
 * </ul>
 *
 * <p>따라서 이 클래스는 반드시 최상위 패키지({@code com.sk.skala.shopapi})에 있어야 한다.
 * 하위 패키지로 내리면 그 바깥의 컴포넌트가 스캔되지 않는다.
 */
@SpringBootApplication
public class ShopApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApiApplication.class, args);
    }
}
