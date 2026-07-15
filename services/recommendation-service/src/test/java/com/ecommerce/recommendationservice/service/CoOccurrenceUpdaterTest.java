package com.ecommerce.recommendationservice.service;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.ConsumedOrderDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import com.ecommerce.recommendationservice.repository.ConsumedOrderRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoOccurrenceUpdaterTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ConsumedOrderRepository consumedOrderRepository;

    @InjectMocks
    private CoOccurrenceUpdater updater;

    @BeforeEach
    void setUp() {
        when(consumedOrderRepository.insert(any(ConsumedOrderDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void should_increment_six_pair_counters_when_order_has_three_distinct_products() {
        // Act
        boolean ingested = updater.ingestOrder("ord-1", "user-1", List.of("p1", "p2", "p3"));

        // Assert: 3 distinct products → 3*(3-1) = 6 directed pair upserts
        assertThat(ingested).isTrue();
        verify(mongoTemplate, times(6))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
        verify(mongoTemplate, times(1))
                .upsert(any(Query.class), any(Update.class), eq(UserPurchaseDocument.class));
        verify(consumedOrderRepository, times(1)).insert(any(ConsumedOrderDocument.class));
    }

    @Test
    void should_upsert_user_purchases_with_atomic_addToSet_each() throws Exception {
        // Act
        updater.ingestOrder("ord-atomic", "user-1", List.of("p1", "p2"));

        // Assert: the user-purchase write must be a single atomic $addToSet $each,
        // NOT a read-modify-write (which races and drops history under concurrency).
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(),
                eq(UserPurchaseDocument.class));

        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo("user-1");

        Document updateObject = updateCaptor.getValue().getUpdateObject();
        assertThat(updateObject).containsKey("$addToSet");
        Document addToSet = (Document) updateObject.get("$addToSet");
        // Spring stores an Update.Each wrapper under the field; its getValue()
        // exposes the $each payload.
        Object each = addToSet.get("productIds");
        java.lang.reflect.Method getValue = each.getClass().getMethod("getValue");
        getValue.setAccessible(true);
        Object eachValues = getValue.invoke(each);
        assertThat((Object[]) eachValues).containsExactly("p1", "p2");

        // updatedAt is bumped via $set, and productIds must NOT be overwritten
        // wholesale — that would reintroduce the lost-history race.
        assertThat(updateObject).containsKey("$set");
        assertThat((Document) updateObject.get("$set")).doesNotContainKey("productIds");
    }

    @Test
    void should_not_read_user_document_before_writing_purchases() {
        // The fix removes the findById → merge → save round-trip entirely; only
        // the atomic upsert path may touch Mongo for user purchases.
        updater.ingestOrder("ord-noread", "user-1", List.of("p1", "p2"));

        verify(mongoTemplate, never()).findById(any(), eq(UserPurchaseDocument.class));
        verify(mongoTemplate, never()).save(any(UserPurchaseDocument.class));
    }

    @Test
    void should_be_idempotent_when_same_orderId_processed_twice() {
        // Arrange — first call succeeds, second hits the unique constraint
        when(consumedOrderRepository.insert(any(ConsumedOrderDocument.class)))
                .thenReturn(ConsumedOrderDocument.builder().orderId("ord-1").build())
                .thenThrow(new DuplicateKeyException("dup orderId"));

        // Act
        boolean firstRun = updater.ingestOrder("ord-1", "user-1", List.of("p1", "p2", "p3"));
        boolean secondRun = updater.ingestOrder("ord-1", "user-1", List.of("p1", "p2", "p3"));

        // Assert
        assertThat(firstRun).isTrue();
        assertThat(secondRun).isFalse();
        verify(mongoTemplate, times(6))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
        verify(mongoTemplate, times(1))
                .upsert(any(Query.class), any(Update.class), eq(UserPurchaseDocument.class));
    }

    @Test
    void should_skip_when_order_has_no_products() {
        boolean ingested = updater.ingestOrder("ord-empty", "user-1", List.of());

        assertThat(ingested).isFalse();
        verifyNoInteractions(mongoTemplate);
        verify(consumedOrderRepository, never()).insert(any(ConsumedOrderDocument.class));
    }

    @Test
    void should_skip_when_orderId_is_blank() {
        boolean ingested = updater.ingestOrder("", "user-1", List.of("p1", "p2"));

        assertThat(ingested).isFalse();
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void should_increment_two_pair_counters_when_order_has_two_distinct_products() {
        boolean ingested = updater.ingestOrder("ord-2", "user-1", List.of("p1", "p2"));

        assertThat(ingested).isTrue();
        verify(mongoTemplate, times(2))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
    }

    @Test
    void should_not_increment_self_pair_when_order_has_single_product() {
        boolean ingested = updater.ingestOrder("ord-3", "user-1", List.of("p1"));

        assertThat(ingested).isTrue();
        // 1 distinct product → 0 pair updates, but user_purchases is still upserted.
        verify(mongoTemplate, never())
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
        verify(mongoTemplate, times(1))
                .upsert(any(Query.class), any(Update.class), eq(UserPurchaseDocument.class));
    }

    @Test
    void should_dedupe_repeated_productId_within_single_order() {
        // p1 appears twice — must collapse to a single distinct product.
        boolean ingested = updater.ingestOrder("ord-4", "user-1", List.of("p1", "p1", "p2"));

        assertThat(ingested).isTrue();
        // 2 distinct → 2 directed pair updates, NOT 6.
        verify(mongoTemplate, times(2))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
    }

    @Test
    void should_skip_user_purchase_upsert_when_userId_is_null() {
        boolean ingested = updater.ingestOrder("ord-6", null, List.of("p1", "p2"));

        assertThat(ingested).isTrue();
        verify(mongoTemplate, never())
                .upsert(any(Query.class), any(Update.class), eq(UserPurchaseDocument.class));
        // Pair updates still run.
        verify(mongoTemplate, times(2))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
    }
}
