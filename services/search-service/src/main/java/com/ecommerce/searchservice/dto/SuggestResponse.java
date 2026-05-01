package com.ecommerce.searchservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Single typeahead suggestion returned by {@code GET /api/search/suggest}.
 *
 * <p>The {@code highlight} field contains the matched span wrapped in
 * {@code <em>...</em>} tags so the front-end can render the prefix match
 * visually without re-doing the substring math.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuggestResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String productId;
    private String name;
    private String image;
    private BigDecimal price;
    private String highlight;
}
