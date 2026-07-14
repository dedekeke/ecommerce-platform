package com.ecommerce.productservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, serialization-safe paged response envelope.
 *
 * <p>Spring Data's {@code PageImpl} has no guaranteed JSON structure and fails
 * serialization on the current Spring Boot version. This DTO exposes only the
 * fields clients depend on, in a shape that mirrors the historical {@code Page}
 * JSON ({@code content}, {@code number}, {@code size}, {@code totalElements},
 * {@code totalPages}) so existing consumers keep working unchanged.
 *
 * @param <T> element type of the page content
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        int numberOfElements,
        boolean empty
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements(),
                page.isEmpty()
        );
    }
}
