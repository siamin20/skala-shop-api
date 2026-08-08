package com.sk.skala.shopapi.product.dto;

import java.util.List;

/**
 * 카테고리 목록. (D35)
 *
 * <p>화면이 탭을 직접 하드코딩하지 않게 서버가 알려준다. 상품이 추가되면서
 * 새 분류가 생겨도 화면을 고칠 필요가 없다.
 *
 * @param category      대분류
 * @param subcategories 그 안의 소분류
 */
public record CategoryResponse(String category, List<String> subcategories) {
}
