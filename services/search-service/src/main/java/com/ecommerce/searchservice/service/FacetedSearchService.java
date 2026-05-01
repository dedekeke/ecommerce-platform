package com.ecommerce.searchservice.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.RangeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.json.JsonData;
import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.FacetBucket;
import com.ecommerce.searchservice.dto.FacetedSearchRequest;
import com.ecommerce.searchservice.dto.FacetedSearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Faceted search (§3.12).
 *
 * <p>Builds a single Elasticsearch query that:
 * <ol>
 *   <li>Combines an optional free-text {@code multi_match} (matched against
 *       {@code name} and {@code description}) with filter clauses for
 *       category, brand, price range, and minimum rating. Filters live in
 *       a {@code bool.filter} clause so they don't affect relevance scoring
 *       and don't influence the aggregations applied to the same query.</li>
 *   <li>Attaches up to four facet aggregations: {@code terms} on
 *       {@code category} and {@code brand}, plus {@code range} aggregations
 *       on {@code price} (4 buckets) and {@code rating} (2 buckets:
 *       {@code 4+}, {@code 3+}). Buckets are post-processed to a stable
 *       {@code FacetBucket} response shape that the frontend can render
 *       without knowing the ES JSON structure.</li>
 * </ol>
 *
 * <p>Responses are cached in Redis for 30 seconds. The cache key is a SHA-256
 * digest of the (query, filters, requested-facets) tuple — that gives us a
 * fixed-length, collision-resistant key without leaking the original query
 * into Redis.
 */
@Service
@Slf4j
public class FacetedSearchService {

    static final String INDEX = "products";
    static final String CACHE_KEY_PREFIX = "search-service:facets:";

    private final ElasticsearchOperations elasticsearchOperations;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public FacetedSearchService(
            ElasticsearchOperations elasticsearchOperations,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${search.facets.cache-ttl-seconds:30}") long cacheTtlSeconds) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    public FacetedSearchResponse search(FacetedSearchRequest request) {
        String cacheKey = cacheKey(request);
        Optional<FacetedSearchResponse> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            log.debug("facets cache HIT key={}", cacheKey);
            return cached.get();
        }

        int page = request.getPage() != null ? Math.max(0, request.getPage()) : 0;
        int size = request.getSize() != null ? Math.min(Math.max(1, request.getSize()), 100) : 20;

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(buildQuery(request))
                .withPageable(PageRequest.of(page, size));
        buildAggregations(request).forEach(builder::withAggregation);
        NativeQuery nativeQuery = builder.build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(
                nativeQuery, ProductDocument.class, IndexCoordinates.of(INDEX));

        FacetedSearchResponse response = FacetedSearchResponse.builder()
                .products(hits.stream().map(SearchHit::getContent).toList())
                .totalElements(hits.getTotalHits())
                .facets(extractFacets(hits.getAggregations()))
                .build();

        writeCache(cacheKey, response);
        return response;
    }

    // visible for testing
    Query buildQuery(FacetedSearchRequest request) {
        return Query.of(q -> q.bool(BoolQuery.of(b -> {
            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                String text = request.getQuery();
                b.must(m -> m.multiMatch(mm -> mm.query(text).fields("name", "description")));
            }
            if (request.getCategories() != null && !request.getCategories().isEmpty()) {
                b.filter(f -> f.terms(termsQuery("category", request.getCategories())));
            }
            if (request.getBrands() != null && !request.getBrands().isEmpty()) {
                b.filter(f -> f.terms(termsQuery("brand", request.getBrands())));
            }
            if (request.getMinPrice() != null || request.getMaxPrice() != null) {
                b.filter(f -> f.range(rangeQuery("price",
                        request.getMinPrice(), request.getMaxPrice())));
            }
            if (request.getMinRating() != null) {
                b.filter(f -> f.range(rangeQuery("rating",
                        BigDecimal.valueOf(request.getMinRating()), null)));
            }
            return b;
        })));
    }

    private static TermsQuery termsQuery(String field, List<String> values) {
        return TermsQuery.of(t -> t
                .field(field)
                .terms(TermsQueryField.of(tf -> tf
                        .value(values.stream().map(FieldValue::of).toList()))));
    }

    private static RangeQuery rangeQuery(String field, BigDecimal gte, BigDecimal lte) {
        return RangeQuery.of(r -> {
            r.field(field);
            if (gte != null) {
                r.gte(JsonData.of(gte));
            }
            if (lte != null) {
                r.lte(JsonData.of(lte));
            }
            return r;
        });
    }

    // visible for testing
    Map<String, Aggregation> buildAggregations(FacetedSearchRequest request) {
        List<String> requested = (request.getFacets() == null || request.getFacets().isEmpty())
                ? List.of("category", "brand", "priceRange", "rating")
                : request.getFacets();

        Map<String, Aggregation> aggs = new LinkedHashMap<>();
        if (requested.contains("category")) {
            aggs.put("category", termsAgg("category", 50));
        }
        if (requested.contains("brand")) {
            aggs.put("brand", termsAgg("brand", 50));
        }
        if (requested.contains("priceRange")) {
            aggs.put("priceRange", priceRangeAgg());
        }
        if (requested.contains("rating")) {
            aggs.put("rating", ratingRangeAgg());
        }
        return aggs;
    }

    private static Aggregation termsAgg(String field, int size) {
        return Aggregation.of(a -> a.terms(TermsAggregation.of(t -> t.field(field).size(size))));
    }

    /**
     * Four price buckets: 0-100, 100-500, 500-1000, 1000+. The `to` value is
     * exclusive in ES range aggregations, so the buckets are non-overlapping.
     */
    private static Aggregation priceRangeAgg() {
        return Aggregation.of(a -> a.range(RangeAggregation.of(r -> r
                .field("price")
                .ranges(
                        AggregationRange.of(b -> b.key("0-100").to("100")),
                        AggregationRange.of(b -> b.key("100-500").from("100").to("500")),
                        AggregationRange.of(b -> b.key("500-1000").from("500").to("1000")),
                        AggregationRange.of(b -> b.key("1000+").from("1000"))))));
    }

    /**
     * Two cumulative rating buckets: {@code 4+} and {@code 3+}. They overlap
     * by design — a 4-star product appears in both. UIs typically render
     * these as filter chips ("4 stars and up", "3 stars and up").
     */
    private static Aggregation ratingRangeAgg() {
        return Aggregation.of(a -> a.range(RangeAggregation.of(r -> r
                .field("rating")
                .ranges(
                        AggregationRange.of(b -> b.key("4+").from("4")),
                        AggregationRange.of(b -> b.key("3+").from("3"))))));
    }

    private Map<String, List<FacetBucket>> extractFacets(AggregationsContainer<?> container) {
        if (!(container instanceof ElasticsearchAggregations aggs)) {
            return Map.of();
        }
        Map<String, List<FacetBucket>> result = new LinkedHashMap<>();
        for (ElasticsearchAggregation agg : aggs.aggregations()) {
            String name = agg.aggregation().getName();
            Aggregate aggregate = agg.aggregation().getAggregate();
            List<FacetBucket> buckets = toBuckets(aggregate);
            if (!buckets.isEmpty()) {
                result.put(name, buckets);
            }
        }
        return result;
    }

    private static List<FacetBucket> toBuckets(Aggregate aggregate) {
        if (aggregate.isSterms()) {
            return aggregate.sterms().buckets().array().stream()
                    .map(b -> FacetBucket.builder()
                            .value(b.key().stringValue())
                            .count(b.docCount())
                            .build())
                    .sorted(Comparator.comparingLong(FacetBucket::getCount).reversed())
                    .toList();
        }
        if (aggregate.isLterms()) {
            return aggregate.lterms().buckets().array().stream()
                    .map(b -> FacetBucket.builder()
                            .value(b.keyAsString() != null ? b.keyAsString() : String.valueOf(b.key()))
                            .count(b.docCount())
                            .build())
                    .sorted(Comparator.comparingLong(FacetBucket::getCount).reversed())
                    .toList();
        }
        if (aggregate.isRange()) {
            return aggregate.range().buckets().array().stream()
                    .map(b -> FacetBucket.builder()
                            .value(b.key())
                            .count(b.docCount())
                            .build())
                    .toList();
        }
        return List.of();
    }

    // visible for testing
    String cacheKey(FacetedSearchRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("q=").append(nullSafe(request.getQuery())).append('|');
        sb.append("c=").append(joinList(request.getCategories())).append('|');
        sb.append("b=").append(joinList(request.getBrands())).append('|');
        sb.append("min=").append(nullSafe(request.getMinPrice())).append('|');
        sb.append("max=").append(nullSafe(request.getMaxPrice())).append('|');
        sb.append("r=").append(nullSafe(request.getMinRating())).append('|');
        sb.append("f=").append(joinList(request.getFacets())).append('|');
        sb.append("p=").append(nullSafe(request.getPage())).append('|');
        sb.append("s=").append(nullSafe(request.getSize()));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return CACHE_KEY_PREFIX + hex;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM — this branch is dead in practice.
            return CACHE_KEY_PREFIX + Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        // Sort so {[a,b]} and {[b,a]} hash to the same key — order is irrelevant
        // for filter semantics.
        return new ArrayList<>(values).stream().sorted().toList().toString();
    }

    private Optional<FacetedSearchResponse> readCache(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, FacetedSearchResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached faceted response key={}", key, e);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.debug("Redis read failed for faceted cache key={}", key);
            return Optional.empty();
        }
    }

    private void writeCache(String key, FacetedSearchResponse value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, cacheTtl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize faceted response for cache key={}", key, e);
        } catch (RuntimeException e) {
            log.debug("Redis write failed for faceted cache key={}", key);
        }
    }
}
