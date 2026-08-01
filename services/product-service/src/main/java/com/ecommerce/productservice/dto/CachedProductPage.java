package com.ecommerce.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis-serialization-safe snapshot of a paged product listing.
 *
 * <p>Holds already-mapped {@link ProductResponse} DTOs, not entities. Caching raw
 * {@code Product} entities is unsafe: the polymorphic JSON serializer writes each
 * value's runtime type into an {@code @class} header, and Hibernate runtime types
 * (a {@code PersistentSet} for the EAGER {@code images}, a lazy {@code category}
 * proxy) either fail to deserialize with no Session, or leak proxy artifacts. DTOs
 * are plain POJOs computed once under the open session, so they round-trip cleanly
 * and are independent of any Hibernate session on cache hit.
 *
 * <p>Spring Data's {@code PageImpl} is likewise not cached (no no-arg constructor);
 * only {@code content} + {@code totalElements} are stored and {@link #toPage}
 * rebuilds a page from the caller's live {@link Pageable}, preserving
 * number/size/sort (same rationale as {@link PageResponse}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedProductPage implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private List<ProductResponse> content;
    private long totalElements;

    public static CachedProductPage of(List<ProductResponse> content, long totalElements) {
        return new CachedProductPage(new ArrayList<>(content), totalElements);
    }

    public Page<ProductResponse> toPage(Pageable pageable) {
        return new PageImpl<>(content, pageable, totalElements);
    }
}
