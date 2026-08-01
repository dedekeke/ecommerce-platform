package com.ecommerce.searchservice.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.RangeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.FacetBucket;
import com.ecommerce.searchservice.dto.FacetedSearchRequest;
import com.ecommerce.searchservice.dto.FacetedSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FacetedSearchService}.
 *
 * <p>We mock {@link ElasticsearchOperations} so the test does not need a
 * running cluster. The first set of tests asserts the structural shape of
 * the {@link NativeQuery} we build (filter clauses, facet aggregations
 * present). The second set stubs an aggregation response and asserts the
 * service correctly transforms ES buckets into our {@link FacetBucket}
 * response shape.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FacetedSearchService")
class FacetedSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private FacetedSearchService service;

    @BeforeEach
    void setUp() {
        service = new FacetedSearchService(elasticsearchOperations, redis, new ObjectMapper(), 30L);
    }

    @Nested
    @DisplayName("query construction")
    class QueryConstruction {

        @Test
        @DisplayName("should_buildBoolFilterClauseWithCategoryAndBrand_when_filtersProvided")
        void should_buildBoolFilterClauseWithCategoryAndBrand_when_filtersProvided() {
            FacetedSearchRequest request = FacetedSearchRequest.builder()
                    .query("laptop")
                    .categories(List.of("Electronics", "Books"))
                    .brands(List.of("Apple"))
                    .minPrice(new BigDecimal("100"))
                    .maxPrice(new BigDecimal("500"))
                    .minRating(4.0)
                    .facets(List.of("category", "brand", "priceRange", "rating"))
                    .build();

            Query built = service.buildQuery(request);

            assertThat(built.isBool()).isTrue();
            String json = built.toString();
            assertThat(json).contains("\"filter\"");
            assertThat(json).contains("category");
            assertThat(json).contains("Electronics");
            assertThat(json).contains("Books");
            assertThat(json).contains("brand");
            assertThat(json).contains("Apple");
            assertThat(json).contains("price");
            assertThat(json).contains("rating");
            // free-text leg ends up in the must clause
            assertThat(json).contains("laptop");
        }

        @Test
        @DisplayName("should_omitFilters_when_noneProvided")
        void should_omitFilters_when_noneProvided() {
            FacetedSearchRequest request = FacetedSearchRequest.builder()
                    .query("phone")
                    .build();

            Query built = service.buildQuery(request);

            // With no filters we still wrap in a bool so we have a place to attach
            // a must clause for the free-text query.
            assertThat(built.isBool()).isTrue();
            String json = built.toString();
            assertThat(json).contains("phone");
            assertThat(json).doesNotContain("Electronics");
        }

        @Test
        @DisplayName("should_buildAllFourAggregations_when_facetsRequested")
        void should_buildAllFourAggregations_when_facetsRequested() {
            FacetedSearchRequest request = FacetedSearchRequest.builder()
                    .query("laptop")
                    .facets(List.of("category", "brand", "priceRange", "rating"))
                    .build();

            Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregation> aggs =
                    service.buildAggregations(request);

            assertThat(aggs).containsKeys("category", "brand", "priceRange", "rating");
            assertThat(aggs.get("category").isTerms()).isTrue();
            assertThat(aggs.get("brand").isTerms()).isTrue();
            assertThat(aggs.get("priceRange").isRange()).isTrue();
            assertThat(aggs.get("rating").isRange()).isTrue();

            assertThat(aggs.get("priceRange").range().ranges()).hasSize(4);
            assertThat(aggs.get("rating").range().ranges()).hasSize(2);
        }

        @Test
        @DisplayName("should_skipUnrequestedAggregations")
        void should_skipUnrequestedAggregations() {
            FacetedSearchRequest request = FacetedSearchRequest.builder()
                    .facets(List.of("category"))
                    .build();

            Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregation> aggs =
                    service.buildAggregations(request);

            assertThat(aggs).containsOnlyKeys("category");
        }

        @Test
        @DisplayName("should_returnAllAggregations_when_facetsListIsNullOrEmpty")
        void should_returnAllAggregations_when_facetsListIsNullOrEmpty() {
            FacetedSearchRequest empty = FacetedSearchRequest.builder().build();
            assertThat(service.buildAggregations(empty)).hasSize(4);

            FacetedSearchRequest none = FacetedSearchRequest.builder()
                    .facets(Collections.emptyList()).build();
            assertThat(service.buildAggregations(none)).hasSize(4);
        }
    }

    @Nested
    @DisplayName("response transformation")
    class ResponseTransformation {

        @Test
        @DisplayName("should_returnEmptyResponse_when_noHitsAndNoAggregations")
        void should_returnEmptyResponse_when_noHitsAndNoAggregations() {
            stubCacheMiss();

            SearchHits<ProductDocument> emptyHits = new SearchHitsImpl<>(
                    0L, TotalHitsRelation.EQUAL_TO, 0.0f, null, null,
                    Collections.<SearchHit<ProductDocument>>emptyList(), null, null);

            when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class),
                    any(IndexCoordinates.class))).thenReturn(emptyHits);

            FacetedSearchRequest request = FacetedSearchRequest.builder()
                    .query("laptop")
                    .facets(List.of("category"))
                    .build();

            FacetedSearchResponse response = service.search(request);

            assertThat(response.getProducts()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
            assertThat(response.getFacets()).isEmpty();
        }

        @Test
        @DisplayName("should_mapTermsAggregations_intoFacetBuckets_inDescendingCountOrder")
        void should_mapTermsAggregations_intoFacetBuckets_inDescendingCountOrder() {
            stubCacheMiss();

            Aggregate categoryAgg = mockStringTerms(List.of(
                    bucket("Electronics", 25),
                    bucket("Books", 17)));
            Aggregate brandAgg = mockStringTerms(List.of(bucket("Apple", 18)));

            ElasticsearchAggregations agg = wrap(Map.of(
                    "category", categoryAgg,
                    "brand", brandAgg));

            SearchHits<ProductDocument> hits = stubHitsWithAggregations(
                    42L, agg, List.of(sampleProduct()));

            when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class),
                    any(IndexCoordinates.class))).thenReturn(hits);

            FacetedSearchResponse response = service.search(FacetedSearchRequest.builder()
                    .query("laptop")
                    .facets(List.of("category", "brand"))
                    .build());

            assertThat(response.getTotalElements()).isEqualTo(42L);
            assertThat(response.getProducts()).hasSize(1);
            assertThat(response.getFacets()).containsKey("category");
            assertThat(response.getFacets()).containsKey("brand");
            assertThat(response.getFacets().get("category"))
                    .extracting(FacetBucket::getValue, FacetBucket::getCount)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Electronics", 25L),
                            org.assertj.core.groups.Tuple.tuple("Books", 17L));
            assertThat(response.getFacets().get("brand"))
                    .extracting(FacetBucket::getValue, FacetBucket::getCount)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("Apple", 18L));
        }

        @Test
        @DisplayName("should_mapRangeAggregations_forPriceAndRating_withReadableLabels")
        void should_mapRangeAggregations_forPriceAndRating_withReadableLabels() {
            stubCacheMiss();

            Aggregate priceAgg = mockRange(List.of(
                    rangeBucket("0-100", 5L),
                    rangeBucket("100-500", 22L),
                    rangeBucket("500-1000", 10L),
                    rangeBucket("1000+", 5L)));

            Aggregate ratingAgg = mockRange(List.of(
                    rangeBucket("4+", 30L),
                    rangeBucket("3+", 35L)));

            ElasticsearchAggregations agg = wrap(Map.of(
                    "priceRange", priceAgg,
                    "rating", ratingAgg));

            SearchHits<ProductDocument> hits = stubHitsWithAggregations(70L, agg, List.of(sampleProduct()));

            when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class),
                    any(IndexCoordinates.class))).thenReturn(hits);

            FacetedSearchResponse response = service.search(FacetedSearchRequest.builder()
                    .query("laptop")
                    .facets(List.of("priceRange", "rating"))
                    .build());

            assertThat(response.getFacets().get("priceRange"))
                    .extracting(FacetBucket::getValue, FacetBucket::getCount)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("0-100", 5L),
                            org.assertj.core.groups.Tuple.tuple("100-500", 22L),
                            org.assertj.core.groups.Tuple.tuple("500-1000", 10L),
                            org.assertj.core.groups.Tuple.tuple("1000+", 5L));

            assertThat(response.getFacets().get("rating"))
                    .extracting(FacetBucket::getValue, FacetBucket::getCount)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("4+", 30L),
                            org.assertj.core.groups.Tuple.tuple("3+", 35L));
        }
    }

    @Nested
    @DisplayName("redis cache")
    class CacheBehaviour {

        @Test
        @DisplayName("should_returnCachedResponse_when_cacheHit")
        void should_returnCachedResponse_when_cacheHit() throws Exception {
            FacetedSearchRequest request = FacetedSearchRequest.builder()
                    .query("laptop")
                    .facets(List.of("category"))
                    .build();

            FacetedSearchResponse cached = FacetedSearchResponse.builder()
                    .totalElements(7L)
                    .products(List.of())
                    .facets(Map.of("category", List.of(FacetBucket.builder().value("X").count(1L).build())))
                    .build();
            String json = new ObjectMapper().writeValueAsString(cached);

            String key = service.cacheKey(request);
            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(eq(key))).thenReturn(json);

            FacetedSearchResponse response = service.search(request);

            assertThat(response.getTotalElements()).isEqualTo(7L);
            verify(elasticsearchOperations, never()).search(any(NativeQuery.class), any(), any(IndexCoordinates.class));
        }

        @Test
        @DisplayName("should_writeCache_with30sTtl_when_cacheMisses")
        void should_writeCache_with30sTtl_when_cacheMisses() {
            stubCacheMiss();

            SearchHits<ProductDocument> empty = new SearchHitsImpl<>(
                    0L, TotalHitsRelation.EQUAL_TO, 0.0f, null, null,
                    Collections.<SearchHit<ProductDocument>>emptyList(), null, null);
            when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class),
                    any(IndexCoordinates.class))).thenReturn(empty);

            FacetedSearchRequest request = FacetedSearchRequest.builder().query("phone").build();
            service.search(request);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, times(1)).set(keyCaptor.capture(), anyString(), eq(java.time.Duration.ofSeconds(30L)));
            assertThat(keyCaptor.getValue()).startsWith("search-service:facets:");
        }

        @Test
        @DisplayName("cacheKey_should_beStableForSameRequest_andDifferentForDifferentRequest")
        void cacheKey_should_beStableForSameRequest_andDifferentForDifferentRequest() {
            FacetedSearchRequest a = FacetedSearchRequest.builder()
                    .query("laptop")
                    .categories(List.of("Electronics"))
                    .minPrice(new BigDecimal("100"))
                    .build();
            FacetedSearchRequest aPrime = FacetedSearchRequest.builder()
                    .query("laptop")
                    .categories(List.of("Electronics"))
                    .minPrice(new BigDecimal("100"))
                    .build();
            FacetedSearchRequest b = FacetedSearchRequest.builder()
                    .query("phone")
                    .build();

            assertThat(service.cacheKey(a)).isEqualTo(service.cacheKey(aPrime));
            assertThat(service.cacheKey(a)).isNotEqualTo(service.cacheKey(b));
        }
    }

    // ----- helpers ---------------------------------------------------------

    private void stubCacheMiss() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null);
    }

    private static ProductDocument sampleProduct() {
        return ProductDocument.builder()
                .id("p-1")
                .name("Laptop")
                .price(new BigDecimal("999.00"))
                .build();
    }

    private static StringTermsBucket bucket(String key, long count) {
        return StringTermsBucket.of(b -> b
                .key(co.elastic.clients.elasticsearch._types.FieldValue.of(key))
                .docCount(count));
    }

    private static Aggregate mockStringTerms(List<StringTermsBucket> buckets) {
        StringTermsAggregate sterms = StringTermsAggregate.of(s -> s
                .buckets(bb -> bb.array(buckets))
                .docCountErrorUpperBound(0L)
                .sumOtherDocCount(0L));
        return Aggregate.of(a -> a.sterms(sterms));
    }

    private static RangeBucket rangeBucket(String key, long count) {
        return RangeBucket.of(b -> b.key(key).docCount(count));
    }

    private static Aggregate mockRange(List<RangeBucket> buckets) {
        RangeAggregate range = RangeAggregate.rangeAggregateOf(r -> r.buckets(bb -> bb.array(buckets)));
        return Aggregate.of(a -> a.range(range));
    }

    private static ElasticsearchAggregations wrap(Map<String, Aggregate> aggs) {
        return new ElasticsearchAggregations(aggs);
    }

    private static SearchHits<ProductDocument> stubHitsWithAggregations(
            long total, AggregationsContainer<?> aggs, List<ProductDocument> docs) {
        List<SearchHit<ProductDocument>> hits = docs.stream()
                .map(d -> new SearchHit<>(
                        "products", d.getId(), null, 1.0f, null, Collections.emptyMap(), Collections.emptyMap(),
                        null, null, Collections.emptyList(), d))
                .toList();
        return new SearchHitsImpl<>(total, TotalHitsRelation.EQUAL_TO, 1.0f, null, null,
                hits, aggs, null);
    }
}
