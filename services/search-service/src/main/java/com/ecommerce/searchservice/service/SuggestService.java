package com.ecommerce.searchservice.service;

import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.ecommerce.common.featureflag.FeatureFlags;
import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.SuggestResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Search-as-you-type service (§3.11).
 *
 * <p>Builds a {@code multi_match} query of type
 * {@link TextQueryType#BoolPrefix} that targets the {@code nameSuggest} field
 * plus its automatically-generated {@code _2gram} and {@code _3gram}
 * sub-fields (created by the {@code search_as_you_type} field type). The
 * query is what Elasticsearch's official typeahead recipe recommends.</p>
 *
 * <p>Responses are cached in Redis for {@code search.suggest.cache-ttl-seconds}
 * (default 30s) keyed by the lowercased query + limit. Typeahead traffic is
 * extremely repetitive within a session — the cache shaves the ES round-trip
 * for ~95% of requests.</p>
 */
@Service
@Slf4j
public class SuggestService {

    static final String INDEX = "products";
    static final String FIELD_NAME = "nameSuggest";
    static final String FIELD_2GRAM = "nameSuggest._2gram";
    static final String FIELD_3GRAM = "nameSuggest._3gram";
    static final String CACHE_KEY_PREFIX = "search-service:suggest:";

    /**
     * Feature-flag name (§4.6) gating the suggest endpoint. Default is
     * {@code false} so callers don't get typeahead unless the flag is on
     * for their environment.
     */
    static final String FLAG_SEARCH_TYPEAHEAD = "SEARCH_TYPEAHEAD";

    private final ElasticsearchOperations elasticsearchOperations;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final FeatureFlags featureFlags;

    public SuggestService(
            ElasticsearchOperations elasticsearchOperations,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            FeatureFlags featureFlags,
            @Value("${search.suggest.cache-ttl-seconds:30}") long cacheTtlSeconds) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.featureFlags = featureFlags;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    /**
     * Build the ES query used for typeahead. Public for unit testing.
     */
    public Query buildSuggestQuery(String text) {
        return Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(text)
                .type(TextQueryType.BoolPrefix)
                .fields(FIELD_NAME, FIELD_2GRAM, FIELD_3GRAM))));
    }

    /**
     * Suggest matching products for the given query prefix.
     *
     * @param query query string (must be non-blank)
     * @param limit max number of suggestions (1..50)
     * @return suggestions ranked by ES score
     */
    public List<SuggestResponse> suggest(String query, int limit) {
        // Feature-flagged (§4.6). When the flag is off (the default), the
        // endpoint behaves as if no products matched — the controller still
        // returns 200 with an empty list, so clients don't need to handle a
        // separate disabled state. Flip FEATURE_FLAG_SEARCH_TYPEAHEAD=true
        // (or feature.flag.search-typeahead=true in YAML) to enable.
        if (!featureFlags.isEnabled(FLAG_SEARCH_TYPEAHEAD)) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String cacheKey = CACHE_KEY_PREFIX + query.toLowerCase().trim() + ":" + boundedLimit;

        Optional<List<SuggestResponse>> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            log.debug("suggest cache HIT for key={}", cacheKey);
            return cached.get();
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildSuggestQuery(query))
                .withPageable(PageRequest.of(0, boundedLimit))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(
                nativeQuery, ProductDocument.class, IndexCoordinates.of(INDEX));

        List<SuggestResponse> result = hits.stream()
                .map(SearchHit::getContent)
                .map(doc -> toSuggestion(doc, query))
                .toList();

        writeCache(cacheKey, result);
        return result;
    }

    private SuggestResponse toSuggestion(ProductDocument doc, String query) {
        return SuggestResponse.builder()
                .productId(doc.getId())
                .name(doc.getName())
                .image(doc.getImages() != null && !doc.getImages().isEmpty() ? doc.getImages().get(0) : null)
                .price(doc.getPrice())
                .highlight(buildHighlight(doc.getName(), query))
                .build();
    }

    /**
     * Wrap the matched span in {@code <em>...</em>}. Case-insensitive, single
     * occurrence — good enough for a typeahead. Falls back to the original
     * name when no match is found.
     */
    static String buildHighlight(String name, String query) {
        if (name == null || query == null || query.isBlank()) {
            return name;
        }
        int idx = name.toLowerCase().indexOf(query.toLowerCase());
        if (idx < 0) {
            return name;
        }
        int end = idx + query.length();
        return name.substring(0, idx) + "<em>" + name.substring(idx, end) + "</em>" + name.substring(end);
    }

    private Optional<List<SuggestResponse>> readCache(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            List<SuggestResponse> list = objectMapper.readValue(raw, new TypeReference<>() {});
            return Optional.of(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached suggest response for key={}", key, e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // Redis unreachable: fail-open so typeahead still works.
            log.debug("Redis read failed for suggest cache key={}", key);
            return Optional.empty();
        }
    }

    private void writeCache(String key, List<SuggestResponse> value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, cacheTtl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize suggest response for cache key={}", key, e);
        } catch (RuntimeException e) {
            log.debug("Redis write failed for suggest cache key={}", key);
        }
    }
}
