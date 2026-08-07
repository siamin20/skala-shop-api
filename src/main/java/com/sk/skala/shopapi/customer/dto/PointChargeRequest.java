package com.sk.skala.shopapi.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 포인트 충전 요청.
 *
 * <p>과제 명세 552p의 {@code updateCustomer}는 "포인트 업데이트"라고만 적혀 있고
 * 엔티티를 통째로 받는다. 그대로 두면 <b>클라이언트가 자기 포인트를 임의의 값으로
 * 덮어쓸 수 있다.</b> 그래서 "얼마로 바꾼다"가 아니라 "얼마를 더한다"로 바꿨다.
 *
 * <p>충전 금액만 받으므로 음수를 보내 남의 포인트를 깎거나 자기 잔액을
 * 마음대로 설정하는 경로가 없다. 실제 결제 연동은 이 과제 범위 밖이다.
 *
 * @param amount 충전할 금액. 원 단위 정수
 */
public record PointChargeRequest(
        @NotNull(message = "충전 금액은 필수입니다")
        @Positive(message = "충전 금액은 0보다 커야 합니다")
        Long amount) {
}
