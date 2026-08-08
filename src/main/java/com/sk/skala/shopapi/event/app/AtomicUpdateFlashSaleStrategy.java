package com.sk.skala.shopapi.event.app;

import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 조건부 원자 UPDATE 방식. 교재가 다루지 않는 네 번째 선택지다. (D23)
 *
 * <pre>
 *   UPDATE flash_sale SET remaining = remaining - ? WHERE id = ? AND remaining &gt;= ?
 * </pre>
 *
 * <p><b>읽고-검사하고-쓰는 세 단계를 한 문장으로 합친다.</b>
 * 애플리케이션이 값을 읽지 않으므로 "읽은 값이 낡았다"는 문제 자체가 성립하지 않는다.
 * 갱신 유실을 막을 락이 애초에 필요 없다.
 *
 * <p>비관적 락과 비슷해 보이지만 <b>행을 붙잡는 구간이 다르다.</b>
 * 비관적 락은 SELECT 시점부터 커밋까지 잡고, 이 방식은 UPDATE 문장이 실행되는 순간만 잡는다.
 *
 * <h2>대가</h2>
 *
 * <p>0행이 반환되었을 때 <b>왜 실패했는지 알 수 없다.</b>
 * "수량 부족"인지 "그런 이벤트가 없음"인지 구분되지 않는다.
 * 그래서 실패했을 때만 한 번 더 조회해 원인을 가른다. 성공 경로에는 추가 조회가 없으므로
 * 흔한 경우의 비용은 늘지 않는다.
 *
 * <p>도메인 메서드 {@code FlashSale.claim()}을 거치지 않는 것도 대가다.
 * 규칙이 SQL과 도메인 두 곳에 나뉘어 산다. 그래서 두 곳이 어긋나지 않도록
 * DB의 CHECK 제약을 마지막 방어선으로 함께 둔다.
 */
@Component
@RequiredArgsConstructor
public class AtomicUpdateFlashSaleStrategy implements FlashSaleClaimStrategy {

    private final FlashSaleRepository repository;

    @Override
    public FlashSaleStrategy type() {
        return FlashSaleStrategy.ATOMIC_UPDATE;
    }

    @Override
    public void claim(Long flashSaleId, int quantity) {
        if (repository.decreaseRemaining(flashSaleId, quantity) == 0) {
            throw failureReason(flashSaleId);
        }
    }

    @Override
    public void release(Long flashSaleId, int quantity) {
        if (repository.increaseRemaining(flashSaleId, quantity) == 0) {
            // 되돌릴 수 없다는 것은 처음 수량을 넘긴다는 뜻이다.
            // 조용히 넘어가면 판매 수량이 어긋난 채로 남는다.
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "이벤트 수량을 되돌릴 수 없습니다: " + flashSaleId);
        }
    }

    /**
     * 0행의 원인을 가린다.
     *
     * <p>실패 경로에서만 부른다. 조회 한 번을 더 쓰지만, 실패한 사용자에게
     * "품절입니다"와 "없는 이벤트입니다"를 구분해 알려줄 수 있다.
     */
    private BusinessException failureReason(Long flashSaleId) {
        return repository.findById(flashSaleId)
                .map(sale -> new BusinessException(ErrorCode.SOLD_OUT,
                        "%s의 남은 수량이 부족합니다. 남은 수량: %d"
                                .formatted(sale.getName(), sale.getRemaining())))
                .orElseGet(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "이벤트를 찾을 수 없습니다: " + flashSaleId));
    }
}
