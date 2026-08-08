package com.sk.skala.shopapi.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 포인트 조정 요청. 관리자가 잔액을 특정 값으로 맞출 때 쓴다.
 *
 * <p>과제 명세 552p의 {@code updateCustomer}("존재 확인 후, 포인트 업데이트")에 해당한다.
 * 명세는 {@code Customer} 엔티티를 통째로 받지만 여기서는 조정할 값만 받는다(D3).
 * 엔티티를 받으면 아이디나 비밀번호까지 함께 덮어쓸 수 있는데, 이 API의 목적은 포인트 조정 하나다.
 *
 * <p>{@link PointChargeRequest}와 의미가 다르다. 이쪽은 <b>"얼마로 만든다"</b>이고
 * 저쪽은 <b>"얼마를 더한다"</b>이다. 둘 다 필요한 이유는 D13에 있다.
 *
 * <p>0을 허용하는 이유는 잔액을 0으로 초기화하는 것이 정당한 관리 작업이기 때문이다.
 * 충전({@code @Positive})과 달리 여기서는 0이 의미 있는 값이다.
 *
 * @param point 조정 후 잔액. 원 단위 정수, 0 이상
 */
public record PointUpdateRequest(

        @NotNull(message = "포인트는 필수입니다")
        @PositiveOrZero(message = "포인트는 0 이상이어야 합니다")
        Long point) {
}
