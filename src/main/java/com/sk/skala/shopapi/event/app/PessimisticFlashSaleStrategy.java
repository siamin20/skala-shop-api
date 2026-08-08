package com.sk.skala.shopapi.event.app;

import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.event.domain.FlashSale;
import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 비관적 락 방식. {@code SELECT ... FOR UPDATE}로 행을 잡는다. (D23)
 *
 * <p>정확하다. 행을 잡은 트랜잭션이 끝날 때까지 다른 요청은 멈추므로 초과 판매가
 * 원리적으로 불가능하다. P4-A에서 상품 재고에 쓴 방법과 같다.
 *
 * <p>대가는 <b>모든 요청이 한 줄로 선다</b>는 것이다. 처리량이 "한 건 처리 시간 × 요청 수"에
 * 수렴하고, 대기가 길어지면 커넥션을 붙잡은 채 풀이 고갈된다.
 * 그래서 {@code lock_timeout}이 함께 있어야 한다. (D22)
 */
@Component
@RequiredArgsConstructor
public class PessimisticFlashSaleStrategy implements FlashSaleClaimStrategy {

    private final FlashSaleRepository repository;

    @Override
    public FlashSaleStrategy type() {
        return FlashSaleStrategy.PESSIMISTIC;
    }

    @Override
    public void claim(Long flashSaleId, int quantity) {
        // 락 획득 순서는 FlashSale → Product → Customer다. 이벤트가 가장 먼저다.
        // 뒤집는 경로가 하나라도 생기면 교차 데드락이 난다. (D22)
        lock(flashSaleId).claim(quantity);
    }

    @Override
    public void release(Long flashSaleId, int quantity) {
        lock(flashSaleId).release(quantity);
    }

    private FlashSale lock(Long flashSaleId) {
        return repository.findByIdForUpdate(flashSaleId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "이벤트를 찾을 수 없습니다: " + flashSaleId));
    }
}
