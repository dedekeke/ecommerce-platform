package com.ecommerce.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Generic page envelope matching Spring Data's {@code PageImpl} JSON shape
 * (the default Jackson serialization used by all backend services).
 *
 * <p>Only the fields the GraphQL schema needs are deserialized. The
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} guards us from
 * upstream changes (added fields like {@code sort}, {@code pageable}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageDto<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number
) {
    public static <T> PageDto<T> empty() {
        return new PageDto<>(List.of(), 0L, 0, 0);
    }
}
