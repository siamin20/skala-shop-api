package com.sk.skala.shopapi.product.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.sk.skala.shopapi.global.common.PageResponse;
import com.sk.skala.shopapi.product.app.ProductService;
import com.sk.skala.shopapi.product.dto.ProductCreateRequest;
import com.sk.skala.shopapi.product.dto.ProductResponse;
import com.sk.skala.shopapi.product.dto.CategoryResponse;
import com.sk.skala.shopapi.product.dto.ProductSort;
import com.sk.skala.shopapi.product.dto.ProductUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 상품 API.
 *
 * <p>과제 명세 554~555p에 해당한다. 컨트롤러는 요청을 받아 서비스에 넘기고 결과를 돌려주는 일만 한다.
 * 검증은 {@code @Valid}와 도메인이, 예외를 상태 코드로 옮기는 일은
 * {@link com.sk.skala.shopapi.global.error.GlobalExceptionHandler}가 맡는다.
 * 그래서 여기에는 {@code try-catch}나 if 분기가 없다.
 *
 * <h2>명세와 다른 점</h2>
 *
 * <p>D7: 경로를 REST 규약에 맞췄다. 명세의 컨트롤러 예시와 API 목록표(537p)가 서로 다른데,
 * 목록표 쪽(`GET /api/products`)이 규약에 맞아 그쪽을 따랐다.
 *
 * <pre>
 *   GET    /api/products/list  →  GET    /api/products        컬렉션은 경로 자체로 표현한다
 *   PUT    /api/products       →  PUT    /api/products/{id}   대상을 경로로 지정한다
 *   DELETE /api/products +본문  →  DELETE /api/products/{id}   본문 있는 DELETE는 프록시가 버릴 수 있다
 * </pre>
 *
 * <p>D3: 요청 본문은 엔티티가 아니라 Request DTO로 받는다.
 */
@Tag(name = "상품", description = "상품 등록·조회·수정·삭제")
@RestController
// 쿼리 파라미터의 @Min·@Max는 메서드 파라미터 검증이라 @Validated가 있어야 동작한다.
// 빠뜨리면 예외 없이 조용히 무시되어, 검증이 걸린 줄 알고 넘어가게 된다.
@Validated
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 상품 목록을 페이지 단위로 조회한다.
     *
     * <p>명세는 파라미터 이름이 {@code offset}, {@code count}지만 실제 의미는 페이지 번호와
     * 페이지 크기다({@code PageRequest.of(offset, count)}). {@code offset}은 보통 "건너뛸 레코드 수"를
     * 뜻해 오해를 부르므로, 의미대로 {@code page}와 {@code size}로 받는다.
     *
     * <p>{@code size}에 상한을 두는 이유는, 막지 않으면 {@code ?size=1000000} 한 번으로
     * 전체 테이블을 메모리에 올려 서버를 멈출 수 있기 때문이다.
     *
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1~100)
     */
    @Operation(
            summary = "상품 목록 조회",
            description = "페이지 단위로 상품을 조회한다. sort로 정렬 기준을 지정한다.")
    @GetMapping
    public PageResponse<ProductResponse> getProducts(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100을 넘을 수 없습니다") int size,
            // 열거형으로 받는다. 문자열 정렬 파라미터를 그대로 열면 클라이언트가
            // 아무 필드로나 정렬할 수 있고, 인덱스 없는 필드를 보내면 전체 스캔이 돈다. (D33)
            @RequestParam(defaultValue = "LATEST") ProductSort sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory) {

        return productService.getProducts(page, size, sort, category, subcategory);
    }

    /** 등록된 카테고리 목록. 화면이 탭을 하드코딩하지 않게 서버가 알려준다. (D35) */
    @Operation(summary = "카테고리 목록", description = "등록된 대분류와 소분류를 반환한다.")
    @GetMapping("/categories")
    public java.util.List<CategoryResponse> getCategories() {
        return productService.getCategories();
    }

    /** 상품 하나를 조회한다. 없으면 404가 나간다. */
    @Operation(summary = "상품 상세 조회")
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    /**
     * 상품을 등록한다.
     *
     * <p>201과 함께 {@code Location} 헤더로 만들어진 자원의 주소를 알려준다.
     * 생성 결과를 200으로 내리면 클라이언트가 "새로 만들어졌는지"를 본문을 뜯어봐야 알 수 있다.
     */
    @Operation(summary = "상품 등록")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse created = productService.createProduct(request);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/products/{id}").build(created.id()))
                .body(created);
    }

    /** 상품 정보를 수정한다. */
    @Operation(summary = "상품 수정")
    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {

        return productService.updateProduct(id, request);
    }

    /**
     * 상품을 삭제한다.
     *
     * <p>돌려줄 내용이 없으므로 204로 응답한다. 빈 본문과 함께 200을 주는 것보다 의도가 분명하다.
     */
    @Operation(summary = "상품 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}
