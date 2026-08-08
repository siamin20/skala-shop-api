package com.sk.skala.shopapi.event.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.event.queue.WaitingRoom;
import com.sk.skala.shopapi.event.queue.WaitingRoomProperties;
import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 선착순 이벤트 대기열 API. (D30)
 *
 * <p>흐름은 이렇다.
 *
 * <pre>
 *   POST /api/flash-sales/{id}/queue   줄을 선다 → 순번을 받는다
 *   GET  /api/flash-sales/{id}/queue   순번을 확인한다 (입장 전까지 반복)
 *   POST /api/flash-sales/orders       입장 허가를 받았으면 참여
 *   DELETE /api/flash-sales/{id}/queue 떠난다
 * </pre>
 *
 * <p>대기열이 꺼져 있으면 항상 "입장 가능"을 돌려준다. 화면이 분기를 두지 않아도 되도록
 * 응답 모양을 같게 유지한다.
 */
@Tag(name = "선착순 대기열", description = "가상 대기열. 몰릴 때 거절하는 대신 줄을 세운다.")
@RestController
@RequestMapping("/api/flash-sales/{flashSaleId}/queue")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoom waitingRoom;
    private final WaitingRoomProperties properties;

    @Operation(summary = "대기열 입장", description = "줄을 서고 순번을 받는다. 이미 서 있으면 순번을 유지한다.")
    @PostMapping
    public WaitingRoom.Ticket enter(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable Long flashSaleId) {

        if (!properties.enabled()) {
            // 대기열을 끈 환경(Redis 없음)에서는 그냥 통과시킨다.
            return WaitingRoom.Ticket.pass(principal.customerId());
        }
        return waitingRoom.enter(flashSaleId, principal.customerId());
    }

    @Operation(summary = "순번 확인", description = "지금 몇 번째인지, 입장할 수 있는지 반환한다.")
    @GetMapping
    public WaitingRoom.Ticket position(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable Long flashSaleId) {

        if (!properties.enabled()) {
            return WaitingRoom.Ticket.pass(principal.customerId());
        }
        return waitingRoom.position(flashSaleId, principal.customerId());
    }

    /**
     * 대기열에서 빠진다.
     *
     * <p>참여를 마쳤거나 포기했을 때 부른다. 이 호출이 없으면 앞자리가 비지 않아
     * <b>뒷사람이 영원히 기다린다.</b> 그래서 화면은 성공·실패 어느 쪽이든 반드시 부른다.
     */
    @Operation(summary = "대기열 이탈", description = "줄에서 빠진다. 참여를 마쳤거나 포기했을 때 부른다.")
    @DeleteMapping
    public void leave(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable Long flashSaleId) {

        if (properties.enabled()) {
            waitingRoom.leave(flashSaleId, principal.customerId());
        }
    }
}
