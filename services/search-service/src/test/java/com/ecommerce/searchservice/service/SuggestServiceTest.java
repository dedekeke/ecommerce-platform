package com.ecommerce.searchservice.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.ecommerce.common.featureflag.FeatureFlags;
import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.SuggestResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SuggestService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuggestService")
class SuggestServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SuggestService service;
    /**
     * The feature-flag tests below verify {@code SEARCH_TYPEAHEAD} gating end-to-end.
     * The remaining tests assume the flag is on (the typical local-dev / staging
     * configuration) — that's what this stub gives them.
     */
    private final FeatureFlags allFlagsOn = new FeatureFlags() {
        @Override public boolean isEnabled(String name) { return true; }
        @Override public boolean isEnabled(String name, String userId) { return true; }
    };

    @BeforeEach
    void setUp() {
        service = new SuggestService(elasticsearchOperations, redis, new ObjectMapper(), allFlagsOn, 30L);
    }

    @Test
    @DisplayName("should_buildMultiMatchBoolPrefixQuery_targetingNameSuggestAndShingleSubfields")
    void should_buildMultiMatchBoolPrefixQuery_targetingNameSuggestAndShingleSubfields() {
        Query query = service.buildSuggestQuery("lap");

        // structural assertions on the query DSL
        assertThat(query.isMultiMatch()).isTrue();
        assertThat(query.multiMatch().query()).isEqualTo("lap");
        assertThat(query.multiMatch().type()).isEqualTo(TextQueryType.BoolPrefix);
        assertThat(query.multiMatch().fields())
                .containsExactly("nameSuggest", "nameSuggest._2gram", "nameSuggest._3gram");
    }

    @Test
    @DisplayName("should_serializeQueryJson_containingShingleSubfields_for_lap")
    void should_serializeQueryJson_containingShingleSubfields_for_lap() throws Exception {
        Query query = service.buildSuggestQuery("lap");
        // Round-trip through the elasticsearch-java JSON-P serializer to assert
        // the wire format. We use the query's toString() which delegates to the
        // JSON-P serializer for assertions on the marshalled query body.
        String json = query.toString();

        assertThat(json).contains("multi_match");
        assertThat(json).contains("\"query\":\"lap\"");
        assertThat(json).contains("nameSuggest");
        assertThat(json).contains("nameSuggest._2gram");
        assertThat(json).contains("nameSuggest._3gram");
        assertThat(json.toLowerCase()).contains("bool_prefix");
    }

    @Test
    @DisplayName("should_returnEmptyList_when_queryIsBlank")
    void should_returnEmptyList_when_queryIsBlank() {
        assertThat(service.suggest("", 8)).isEmpty();
        assertThat(service.suggest("   ", 8)).isEmpty();
        assertThat(service.suggest(null, 8)).isEmpty();
        verify(elasticsearchOperations, never()).search(any(NativeQuery.class), any(), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("should_returnCachedResult_when_redisHasFreshEntry")
    void should_returnCachedResult_when_redisHasFreshEntry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        String cached = "[{\"productId\":\"p-1\",\"name\":\"Laptop\",\"price\":999.99,\"highlight\":\"<em>lap</em>top\"}]";
        when(valueOps.get(eq("search-service:suggest:lap:8"))).thenReturn(cached);

        List<SuggestResponse> result = service.suggest("lap", 8);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo("p-1");
        verify(elasticsearchOperations, never()).search(any(NativeQuery.class), any(), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("should_queryElasticsearchAndPopulateCache_when_cacheMisses")
    void should_queryElasticsearchAndPopulateCache_when_cacheMisses() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        ProductDocument doc = ProductDocument.builder()
                .id("p-1")
                .name("Laptop Pro")
                .price(new BigDecimal("999.00"))
                .images(new ArrayList<>(List.of("http://img/laptop.jpg")))
                .build();
        SearchHit<ProductDocument> hit = new SearchHit<>(
                "products", "p-1", null, 1.0f, null, Collections.emptyMap(), Collections.emptyMap(),
                null, null, Collections.emptyList(), doc);
        SearchHits<ProductDocument> hits = new SearchHitsImpl<>(
                1L, TotalHitsRelation.EQUAL_TO, 1.0f, "scroll-1", null,
                List.of(hit), null, null);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class),
                any(IndexCoordinates.class))).thenReturn(hits);

        List<SuggestResponse> result = service.suggest("Lap", 5);

        assertThat(result).hasSize(1);
        SuggestResponse first = result.get(0);
        assertThat(first.getProductId()).isEqualTo("p-1");
        assertThat(first.getName()).isEqualTo("Laptop Pro");
        assertThat(first.getImage()).isEqualTo("http://img/laptop.jpg");
        assertThat(first.getHighlight()).contains("<em>Lap</em>");

        verify(valueOps, times(1)).set(eq("search-service:suggest:lap:5"), anyString(),
                eq(Duration.ofSeconds(30L)));
    }

    @Test
    @DisplayName("should_clampLimitTo50_when_callerPassesHugeLimit")
    void should_clampLimitTo50_when_callerPassesHugeLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        SearchHits<ProductDocument> emptyHits = new SearchHitsImpl<>(
                0L, TotalHitsRelation.EQUAL_TO, 0.0f, null, null,
                Collections.<SearchHit<ProductDocument>>emptyList(), null, null);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class),
                any(IndexCoordinates.class))).thenReturn(emptyHits);

        service.suggest("foo", 9999);

        // The cache key encodes the bounded limit (50), not the raw caller value.
        verify(valueOps).set(eq("search-service:suggest:foo:50"), anyString(), eq(Duration.ofSeconds(30L)));
    }

    @Test
    @DisplayName("buildHighlight_should_wrapMatchInEmTags")
    void buildHighlight_should_wrapMatchInEmTags() {
        assertThat(SuggestService.buildHighlight("Laptop Pro", "lap")).isEqualTo("<em>Lap</em>top Pro");
        assertThat(SuggestService.buildHighlight("Apple Laptop", "Lap")).isEqualTo("Apple <em>Lap</em>top");
    }

    @Test
    @DisplayName("buildHighlight_should_returnOriginal_when_queryNotFound")
    void buildHighlight_should_returnOriginal_when_queryNotFound() {
        assertThat(SuggestService.buildHighlight("Laptop", "xyz")).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("should_returnEmptyList_andNotHitElasticsearch_when_searchTypeaheadFlagDisabled")
    void should_returnEmptyList_andNotHitElasticsearch_when_searchTypeaheadFlagDisabled() {
        FeatureFlags allOff = new FeatureFlags() {
            @Override public boolean isEnabled(String name) { return false; }
            @Override public boolean isEnabled(String name, String userId) { return false; }
        };
        SuggestService offService = new SuggestService(
                elasticsearchOperations, redis, new ObjectMapper(), allOff, 30L);

        List<SuggestResponse> result = offService.suggest("laptop", 8);

        assertThat(result).isEmpty();
        verify(elasticsearchOperations, never())
                .search(any(NativeQuery.class), any(), any(IndexCoordinates.class));
        verify(redis, never()).opsForValue();
    }
}
