package com.ecommerce.productservice.dto;

import com.ecommerce.productservice.model.Product;
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
 * <p>Spring Data's {@code PageImpl} has no no-arg constructor and fails to
 * round-trip through the JSON Redis serializer (the same reason
 * {@link PageResponse} exists for the HTTP layer). This holder is a plain,
 * <b>non-final</b> bean so the configured {@code GenericJackson2JsonRedisSerializer}
 * (default typing = {@code NON_FINAL}) writes an {@code @class} header and can
 * deserialize it back; the {@link Product} elements carry their own type
 * headers. Only {@code content} + the page coordinates are stored — enough to
 * rebuild an in-memory {@link Page} for the caller, never cached itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedProductPage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Product> content;
    private long totalElements;

    public static CachedProductPage of(Page<Product> page) {
        return new CachedProductPage(new ArrayList<>(page.getContent()), page.getTotalElements());
    }

    /**
     * Rebuilds a {@link Page} using the caller's original {@link Pageable} so the
     * page number, size and sort are preserved for the response envelope.
     */
    public Page<Product> toPage(Pageable pageable) {
        return new PageImpl<>(content, pageable, totalElements);
    }
}
