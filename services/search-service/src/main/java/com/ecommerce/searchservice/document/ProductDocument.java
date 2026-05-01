package com.ecommerce.searchservice.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Product Document for Elasticsearch
 * Represents a searchable product with full-text search capabilities
 */
@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-settings.json")
@Mapping(mappingPath = "elasticsearch/product-mapping.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private List<String> images;

    @Field(type = FieldType.Boolean)
    private Boolean active;

    @Field(type = FieldType.Integer)
    private Integer stockQuantity;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    private String nameAutocomplete;

    /**
     * Search-as-you-type field used by {@code GET /api/search/suggest}. ES
     * automatically generates the {@code _2gram}, {@code _3gram} and
     * {@code _index_prefix} sub-fields used by the multi_match query.
     */
    @Field(type = FieldType.Search_As_You_Type, maxShingleSize = 3)
    private String nameSuggest;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    /**
     * Computed field for search score
     */
    @Field(type = FieldType.Double)
    private Double searchScore;

    /**
     * Set by the replenishment saga when a product runs low. The relevance
     * query multiplies the base score by ~0.5 for documents flagged true so
     * unfulfillable items rank lower until stock returns.
     */
    @Field(type = FieldType.Boolean)
    private Boolean lowStockPenalty;
}
