package com.ecommerce.reviewservice.service;

import org.springframework.data.domain.Sort;

/**
 * Allowed sort modes for {@code GET /api/reviews/product/{id}}.
 *
 * <p>Each mode maps to one of the compound indexes declared on
 * {@link com.ecommerce.reviewservice.domain.ReviewDocument}, so every list
 * page is served by an indexed scan.
 */
public enum ReviewSortMode {
    HELPFUL(Sort.by(Sort.Direction.DESC, "helpful").and(Sort.by(Sort.Direction.DESC, "createdAt"))),
    RECENT(Sort.by(Sort.Direction.DESC, "createdAt")),
    RATING(Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt")));

    private final Sort sort;

    ReviewSortMode(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }

    public static ReviewSortMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return HELPFUL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "recent" -> RECENT;
            case "rating" -> RATING;
            default -> HELPFUL;
        };
    }
}
