package com.sk.skala.shopapi.product.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * {@link ProductService} 통합 테스트.
 *
 * <p>리포지토리를 목으로 대체하지 않고 실제 JPA로 돌린다. 이 서비스가 하는 검사가
 * 대부분 "저장소에 물어봐야 알 수 있는 것"(존재 여부, 이름 중복)이라, 목으로 바꾸면
 * 목이 준 답을 확인하는 셈이 되어 검증 가치가 거의 없어지기 때문이다.
 *
 * <p>{@code @Transactional}을 붙여 각 테스트가 끝나면 롤백되게 한다.
 * 테스트끼리 데이터가 섞이지 않고, 실행 순서에 결과가 좌우되지 않는다.
 */
@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // data.sql 시드가 들어 있으므로 비우고 시작한다. 시드에 의존하면
        // 시드가 바뀔 때 관계없는 테스트가 깨진다.
        //
        // deleteAll()이 아니라 deleteAllInBatch()를 쓰는 이유가 있다.
        // deleteAll()은 DELETE를 영속성 컨텍스트에 쌓아두고 flush 시점에 실행하는데,
        // IDENTITY 전략의 save()는 ID를 받아야 하므로 INSERT를 즉시 실행한다.
        // 그래서 시드가 지워지기 전에 같은 이름의 INSERT가 먼저 나가 유니크 제약에 걸린다.
        // deleteAllInBatch()는 DELETE 한 문장을 바로 실행해 이 순서 문제를 없앤다.
        productRepository.deleteAllInBatch();
    }

    private Product 저장된상품(String name, long price) {
        return productRepository.save(new Product(name, Money.of(price)));
    }

    @Nested
    @DisplayName("조회")
    class Read {

        @Test
        @DisplayName("페이지 단위로 조회한다")
        void getProductsByPage() {
            저장된상품("무선마우스", 15_000);
            저장된상품("블루투스키보드", 29_000);
            저장된상품("USB허브", 39_000);

            PageResponse<ProductResponse> first = productService.getProducts(0, 2);

            assertThat(first.content()).hasSize(2);
            assertThat(first.totalElements()).isEqualTo(3);
            assertThat(first.totalPages()).isEqualTo(2);
            assertThat(first.last()).isFalse();
        }

        @Test
        @DisplayName("마지막 페이지를 표시한다")
        void markLastPage() {
            저장된상품("무선마우스", 15_000);
            저장된상품("USB허브", 39_000);

            PageResponse<ProductResponse> last = productService.getProducts(1, 1);

            assertThat(last.content()).hasSize(1);
            assertThat(last.last()).isTrue();
        }

        @Test
        @DisplayName("범위를 넘은 페이지는 빈 목록을 준다")
        void emptyPageBeyondRange() {
            저장된상품("무선마우스", 15_000);

            PageResponse<ProductResponse> page = productService.getProducts(99, 10);

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("ID로 상세를 조회한다")
        void getProductById() {
            Product saved = 저장된상품("무선마우스", 15_000);

            ProductResponse found = productService.getProduct(saved.getId());

            assertThat(found.name()).isEqualTo("무선마우스");
            assertThat(found.price()).isEqualTo(15_000);
        }

        @Test
        @DisplayName("없는 ID를 조회하면 DATA_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> productService.getProduct(9999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("등록")
    class Create {

        @Test
        @DisplayName("새 상품을 등록한다")
        void createProduct() {
            ProductResponse created = productService.createProduct(
                    new ProductCreateRequest("무선마우스", 15_000L));

            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("무선마우스");
            assertThat(productRepository.findByName("무선마우스")).isPresent();
        }

        @Test
        @DisplayName("이름이 중복이면 DATA_DUPLICATED")
        void rejectDuplicateName() {
            저장된상품("무선마우스", 15_000);

            assertThatThrownBy(() -> productService.createProduct(
                    new ProductCreateRequest("무선마우스", 20_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_DUPLICATED);
        }

        @Test
        @DisplayName("앞뒤 공백만 다른 이름도 중복으로 잡는다")
        void rejectDuplicateAfterTrim() {
            저장된상품("무선마우스", 15_000);

            // Product 생성자가 trim하므로 "  무선마우스  "도 같은 이름이 된다.
            // 다듬기 전 값으로 비교하면 이 중복이 그대로 통과한다.
            assertThatThrownBy(() -> productService.createProduct(
                    new ProductCreateRequest("  무선마우스  ", 20_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_DUPLICATED);
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("이름과 가격을 수정한다")
        void updateProduct() {
            Product saved = 저장된상품("무선마우스", 15_000);

            ProductResponse updated = productService.updateProduct(
                    saved.getId(), new ProductUpdateRequest("무선마우스 v2", 18_000L));

            assertThat(updated.name()).isEqualTo("무선마우스 v2");
            assertThat(updated.price()).isEqualTo(18_000);
        }

        @Test
        @DisplayName("이름을 그대로 두고 가격만 바꿀 수 있다")
        void updatePriceOnly() {
            Product saved = 저장된상품("무선마우스", 15_000);

            // 자기 자신을 중복에서 제외하지 않으면 여기서 DATA_DUPLICATED가 잘못 발생한다
            ProductResponse updated = productService.updateProduct(
                    saved.getId(), new ProductUpdateRequest("무선마우스", 18_000L));

            assertThat(updated.name()).isEqualTo("무선마우스");
            assertThat(updated.price()).isEqualTo(18_000);
        }

        @Test
        @DisplayName("다른 상품이 쓰는 이름으로는 바꿀 수 없다")
        void rejectRenameToExistingName() {
            Product mouse = 저장된상품("무선마우스", 15_000);
            저장된상품("USB허브", 39_000);

            assertThatThrownBy(() -> productService.updateProduct(
                    mouse.getId(), new ProductUpdateRequest("USB허브", 20_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_DUPLICATED);
        }

        @Test
        @DisplayName("공백만 다른 이름으로 바꾸려 해도 중복으로 잡는다")
        void rejectRenameToExistingNameAfterTrim() {
            Product mouse = 저장된상품("무선마우스", 15_000);
            저장된상품("USB허브", 39_000);

            // 저장은 Product.normalizeName을 거치는데 중복 검사가 다른 규칙을 쓰면
            // "  USB허브  "가 검사를 통과해 저장 단계에서 유니크 제약으로 터진다.
            // 두 경로가 같은 정규화를 쓰는지 이 테스트가 고정한다.
            assertThatThrownBy(() -> productService.updateProduct(
                    mouse.getId(), new ProductUpdateRequest("  USB허브  ", 20_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_DUPLICATED);
        }

        @Test
        @DisplayName("없는 상품을 수정하면 DATA_NOT_FOUND")
        void updateNotFound() {
            assertThatThrownBy(() -> productService.updateProduct(
                    9999L, new ProductUpdateRequest("무선마우스", 15_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("상품을 삭제한다")
        void deleteProduct() {
            Product saved = 저장된상품("무선마우스", 15_000);

            productService.deleteProduct(saved.getId());

            assertThat(productRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("없는 상품을 삭제하면 DATA_NOT_FOUND")
        void deleteNotFound() {
            // deleteById만 쓰면 없는 ID여도 조용히 성공한 것처럼 보인다
            assertThatThrownBy(() -> productService.deleteProduct(9999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }
}
