package com.ecommerce.recommendationservice.service;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.ConsumedOrderDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import com.ecommerce.recommendationservice.repository.ConsumedOrderRepository;
import com.ecommerce.recommendationservice.repository.UserPurchaseRepository;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

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
    private UserPurchaseRepository userPurchaseRepository;

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
        // Arrange
        when(userPurchaseRepository.findById("user-1")).thenReturn(Optional.empty());

        // Act
        boolean ingested = updater.ingestOrder("ord-1", "user-1", List.of("p1", "p2", "p3"));

        // Assert: 3 distinct products → 3*(3-1) = 6 directed pair upserts
        assertThat(ingested).isTrue();
        verify(mongoTemplate, times(6))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
        verify(userPurchaseRepository, times(1)).save(any(UserPurchaseDocument.class));
        verify(consumedOrderRepository, times(1)).insert(any(ConsumedOrderDocument.class));
    }

    @Test
    void should_be_idempotent_when_same_orderId_processed_twice() {
        // Arrange — first call succeeds, second hits the unique constraint
        when(consumedOrderRepository.insert(any(ConsumedOrderDocument.class)))
                .thenReturn(ConsumedOrderDocument.builder().orderId("ord-1").build())
                .thenThrow(new DuplicateKeyException("dup orderId"));
        when(userPurchaseRepository.findById("user-1")).thenReturn(Optional.empty());

        // Act
        boolean firstRun = updater.ingestOrder("ord-1", "user-1", List.of("p1", "p2", "p3"));
        boolean secondRun = updater.ingestOrder("ord-1", "user-1", List.of("p1", "p2", "p3"));

        // Assert
        assertThat(firstRun).isTrue();
        assertThat(secondRun).isFalse();
        // Only the first run does pair updates; second run returns early after the dup-key trap.
        verify(mongoTemplate, times(6))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
        verify(userPurchaseRepository, times(1)).save(any(UserPurchaseDocument.class));
    }

    @Test
    void should_skip_when_order_has_no_products() {
        boolean ingested = updater.ingestOrder("ord-empty", "user-1", List.of());

        assertThat(ingested).isFalse();
        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(userPurchaseRepository);
        verify(consumedOrderRepository, never()).insert(any(ConsumedOrderDocument.class));
    }

    @Test
    void should_skip_when_orderId_is_blank() {
        boolean ingested = updater.ingestOrder("", "user-1", List.of("p1", "p2"));

        assertThat(ingested).isFalse();
        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(userPurchaseRepository);
    }

    @Test
    void should_increment_two_pair_counters_when_order_has_two_distinct_products() {
        when(userPurchaseRepository.findById("user-1")).thenReturn(Optional.empty());

        boolean ingested = updater.ingestOrder("ord-2", "user-1", List.of("p1", "p2"));

        assertThat(ingested).isTrue();
        verify(mongoTemplate, times(2))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
    }

    @Test
    void should_not_increment_self_pair_when_order_has_single_product() {
        when(userPurchaseRepository.findById("user-1")).thenReturn(Optional.empty());

        boolean ingested = updater.ingestOrder("ord-3", "user-1", List.of("p1"));

        assertThat(ingested).isTrue();
        // 1 distinct product → 1*(1-1) = 0 pair updates, but user_purchases is still saved.
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
        verify(userPurchaseRepository, times(1)).save(any(UserPurchaseDocument.class));
    }

    @Test
    void should_dedupe_repeated_productId_within_single_order() {
        when(userPurchaseRepository.findById("user-1")).thenReturn(Optional.empty());

        // p1 appears twice (e.g. user added two units) — must collapse to a single distinct product.
        boolean ingested = updater.ingestOrder("ord-4", "user-1", List.of("p1", "p1", "p2"));

        assertThat(ingested).isTrue();
        // 2 distinct → 2 directed pair updates, NOT 6.
        verify(mongoTemplate, times(2))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
    }

    @Test
    void should_merge_into_existing_user_purchase_document() {
        UserPurchaseDocument existing = UserPurchaseDocument.builder()
                .userId("user-1")
                .productIds(new HashSet<>(List.of("p0")))
                .build();
        when(userPurchaseRepository.findById("user-1")).thenReturn(Optional.of(existing));

        updater.ingestOrder("ord-5", "user-1", List.of("p1", "p2"));

        ArgumentCaptor<UserPurchaseDocument> captor = ArgumentCaptor.forClass(UserPurchaseDocument.class);
        verify(userPurchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getProductIds()).containsExactlyInAnyOrder("p0", "p1", "p2");
    }

    @Test
    void should_skip_user_purchase_save_when_userId_is_null() {
        boolean ingested = updater.ingestOrder("ord-6", null, List.of("p1", "p2"));

        assertThat(ingested).isTrue();
        verify(userPurchaseRepository, never()).save(any());
        // Pair updates still run.
        verify(mongoTemplate, times(2))
                .upsert(any(Query.class), any(Update.class), eq(CoOccurrenceDocument.class));
    }
}
