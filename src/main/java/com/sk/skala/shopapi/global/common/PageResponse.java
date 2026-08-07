package com.sk.skala.shopapi.global.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 페이지 단위 조회 응답.
 *
 * <p>명세(549p)의 {@code PagedList}에 해당한다. Spring Data의 {@link Page}를 그대로
 * 응답으로 내보내지 않는 이유가 있다. {@code Page}의 JSON 구조는 Spring Data 버전에 따라
 * 바뀌어 왔고(실제로 Boot 3.3부터 직렬화 방식 변경 경고가 나온다), 클라이언트가 쓰지 않는
 * 내부 필드({@code pageable}, {@code sort}, {@code empty} 등)까지 함께 나간다.
 * 응답 형태를 우리가 소유하면 라이브러리 버전에 흔들리지 않는다.
 *
 * <p>명세는 {@code offset}과 {@code count}를 쓰지만 여기서는 {@code page}와 {@code size}로
 * 받는다. 이유는 {@link com.sk.skala.shopapi.product.api.ProductController}에 적었다.
 *
 * @param content       현재 페이지의 항목들
 * @param page          현재 페이지 번호 (0부터)
 * @param size          페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages    전체 페이지 수
 * @param last          마지막 페이지 여부
 * @param <T>           항목 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    /**
     * {@link Page}를 응답으로 옮기면서 각 항목을 변환한다.
     *
     * <p>{@code mapper}를 받는 이유는 엔티티 페이지를 응답 DTO 페이지로 바꾸기 위해서다.
     * 이 변환을 여기서 하면 서비스마다 같은 코드를 반복하지 않는다.
     *
     * @param page   Spring Data 조회 결과
     * @param mapper 항목 변환 함수 (예: {@code ProductResponse::from})
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
