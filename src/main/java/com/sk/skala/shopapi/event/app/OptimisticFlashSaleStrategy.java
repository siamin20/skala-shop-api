package com.sk.skala.shopapi.event.app;

import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.event.domain.FlashSale;
import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 낙관적 락 방식. {@code @Version}으로 충돌을 감지하고 바깥 계층이 재시도한다. (D23)
 *
 * <p><b>선착순 이벤트에 맞지 않는 도구인 줄 알면서 구현했다.</b> 낙관적 락은 경합이
 * 드물다는 전제 위의 전략인데, 여기서는 수천 명이 같은 행을 노려 그 전제가 정면으로 깨진다.
 *
 * <p>그래도 넣은 이유는 <b>얼마나 나빠지는지 재기 위해서</b>다.
 * "선착순에는 낙관적 락이 부적절하다"는 문장을 근거 없이 쓰고 싶지 않았다.
 *
 * <p>충돌하면 예외가 나고 {@code TransactionRetryExecutor}가 새 트랜잭션으로 다시 시도한다.
 * 재시도는 여기가 아니라 트랜잭션 바깥에 있어야 한다. (D22)
 */
@Component
@RequiredArgsConstructor
public class OptimisticFlashSaleStrategy implements FlashSaleClaimStrategy {

    private final FlashSaleRepository repository;

    @Override
    public FlashSaleStrategy type() {
        return FlashSaleStrategy.OPTIMISTIC;
    }

    @Override
    public void claim(Long flashSaleId, int quantity) {
        // 락 없이 읽는다. 다른 트랜잭션도 같은 값을 읽을 수 있고, 그래도 된다.
        // 어긋났다는 사실은 커밋 시점에 버전 비교로 드러난다.
        FlashSale sale = find(flashSaleId);
        sale.claim(quantity);
    }

    @Override
    public void release(Long flashSaleId, int quantity) {
        find(flashSaleId).release(quantity);
    }

    private FlashSale find(Long flashSaleId) {
        return repository.findById(flashSaleId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "이벤트를 찾을 수 없습니다: " + flashSaleId));
    }
}
