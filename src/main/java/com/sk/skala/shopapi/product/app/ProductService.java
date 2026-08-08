package com.sk.skala.shopapi.product.app;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.common.PageResponse;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;
import com.sk.skala.shopapi.product.dto.ProductCreateRequest;
import com.sk.skala.shopapi.product.dto.ProductResponse;
import com.sk.skala.shopapi.product.dto.ProductUpdateRequest;

import lombok.RequiredArgsConstructor;

/**
 * 상품 관리 서비스.
 *
 * <p>과제 명세 549~550p에 해당한다. 조회·등록·수정·삭제를 담당하며,
 * 값 자체의 유효성(이름 비어 있음, 가격 0)은 이 클래스가 아니라 {@link Product}가 검사한다.
 * 여기서 하는 것은 <b>저장소가 있어야만 알 수 있는 검사</b>다. 존재 여부와 이름 중복이 그렇다.
 *
 * <p>클래스에 {@code @Transactional(readOnly = true)}를 걸고 쓰기 메서드에만 다시 선언한다.
 * 기본값을 읽기 전용으로 두면 실수로 쓰기 메서드에 애노테이션을 빠뜨렸을 때
 * 조용히 저장되는 대신 예외가 나서 바로 드러난다. 읽기 전용 트랜잭션은 JPA가 변경 감지를
 * 건너뛰므로 조회 성능에도 유리하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 목록을 페이지 단위로 조회한다.
     *
     * <p>정렬을 지정하지 않으면 DB가 반환하는 순서에 의존하게 되어, 같은 요청에 다른 순서가
     * 나올 수 있다. 그러면 2페이지에 1페이지 항목이 다시 나오거나 어떤 항목은 아예 안 보인다.
     * 그래서 유일한 값인 {@code id}로 정렬을 고정한다.
     *
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기
     */
    public PageResponse<ProductResponse> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Product> products = productRepository.findAll(pageable);
        return PageResponse.of(products, ProductResponse::from);
    }

    /**
     * 상품 하나를 조회한다.
     *
     * @throws BusinessException 없으면 {@link ErrorCode#DATA_NOT_FOUND}
     */
    public ProductResponse getProduct(Long id) {
        return ProductResponse.from(findProductOrThrow(id));
    }

    /**
     * 상품을 등록한다.
     *
     * <p>이름 중복은 DB 유니크 제약으로도 막히지만 여기서 먼저 확인한다.
     * 제약 위반으로 터지면 드라이버 예외가 올라와 사용자에게 무엇이 잘못됐는지 전달할 수 없다.
     * 다만 확인과 저장 사이에 다른 요청이 끼어들 수 있으므로 유니크 제약도 그대로 유지한다.
     * 애플리케이션 검사는 메시지를 위한 것이고, 최종 방어선은 DB다.
     *
     * @throws BusinessException 이름이 중복이면 {@link ErrorCode#DATA_DUPLICATED}
     */
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        // 요청 값이 아니라 정규화된 이름으로 비교해야 " 무선마우스 "가 중복을 피해 가지 않는다.
        Product product = request.toEntity();

        productRepository.findByName(product.getName()).ifPresent(existing -> {
            throw new BusinessException(
                    ErrorCode.DATA_DUPLICATED, "이미 존재하는 상품명입니다: " + product.getName());
        });

        return ProductResponse.from(productRepository.save(product));
    }

    /**
     * 상품 정보를 수정한다.
     *
     * <p>{@code save}를 부르지 않는다. 조회한 엔티티는 영속 상태이므로 값만 바꾸면
     * 트랜잭션이 끝날 때 JPA가 변경을 감지해 자동으로 UPDATE를 실행한다(dirty checking).
     *
     * @throws BusinessException 상품이 없으면 {@link ErrorCode#DATA_NOT_FOUND},
     *                           다른 상품이 같은 이름을 쓰고 있으면 {@link ErrorCode#DATA_DUPLICATED}
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = findProductOrThrow(id);

        // 저장될 값과 같은 규칙으로 비교해야 한다. 서비스가 trim을 직접 부르면
        // 정규화 규칙이 두 곳에 흩어져 나중에 어긋난다.
        String normalizedName = Product.normalizeName(request.name());

        // 자기 자신은 중복에서 제외한다. 이름을 그대로 두고 가격만 바꾸는 경우를 막지 않기 위해서다.
        if (productRepository.existsByNameAndIdNot(normalizedName, id)) {
            throw new BusinessException(
                    ErrorCode.DATA_DUPLICATED, "이미 존재하는 상품명입니다: " + normalizedName);
        }

        product.changeName(request.name());
        product.changePrice(Money.of(request.price()));

        return ProductResponse.from(product);
    }

    /**
     * 상품을 삭제한다.
     *
     * <p>존재하지 않는 ID로 {@code deleteById}를 부르면 조용히 아무 일도 일어나지 않는다.
     * 그러면 클라이언트는 삭제에 성공했다고 오해한다. 먼저 조회해서 없으면 알려준다.
     *
     * <p>주문 내역이 있는 상품은 지울 수 없다. {@code order_item}이 이 상품을 참조하기 때문이다.
     * 여기서 잡지 않으면 외래 키 위반이 전역 처리기까지 올라가
     * "이미 존재하는 데이터입니다"라는 <b>엉뚱한 메시지</b>로 나간다. 사용자는 무엇을 해야 할지 알 수 없다.
     *
     * <p>주문 저장소를 직접 조회해 미리 확인하지 않는 이유는 의존 방향 때문이다.
     * {@code order → product}는 있어도 그 반대는 없다. 상품이 주문을 알게 되면 순환이 생긴다.
     * 대신 {@code flush}로 제약 위반을 이 자리에서 드러내고 의미 있는 예외로 바꾼다.
     *
     * @throws BusinessException 없으면 {@link ErrorCode#DATA_NOT_FOUND},
     *                           주문 내역이 있으면 {@link ErrorCode#DATA_IN_USE}
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductOrThrow(id);

        try {
            productRepository.delete(product);
            // flush가 없으면 DELETE가 트랜잭션 끝에 실행돼 이 try 블록 밖에서 터진다.
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.DATA_IN_USE, "주문 내역이 있는 상품은 삭제할 수 없습니다: " + product.getName());
        }
    }

    /**
     * ID로 상품을 찾고, 없으면 예외를 던진다.
     *
     * <p>조회 후 존재 확인이 네 메서드에서 반복되므로 한곳에 모았다.
     * 메시지 형식도 여기서 통일된다.
     */
    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
    }
}
