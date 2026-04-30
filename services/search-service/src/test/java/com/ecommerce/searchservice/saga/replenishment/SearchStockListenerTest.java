package com.ecommerce.searchservice.saga.replenishment;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchStockListener — choreography participant")
class SearchStockListenerTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ConsumedEventStore consumedStore;
    private SearchStockListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        consumedStore = new ConsumedEventStore();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new SearchStockListener(
                productSearchRepository, consumedStore, kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("should apply low-stock penalty and emit search.deboosted on stock.low.detected")
    void should_deboost_when_stockLowReceived() throws Exception {
        ProductDocument doc = ProductDocument.builder().id("100").name("Phone").build();
        when(productSearchRepository.findById("100")).thenReturn(Optional.of(doc));

        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 2, 5, Instant.now());
        listener.onStockLow(objectMapper.writeValueAsString(event));

        verify(productSearchRepository).save(doc);
        verify(kafkaTemplate).send(eq("search.deboosted.due-to-stock"), anyString(), any(SearchDeboostedEvent.class));
    }

    @Test
    @DisplayName("should clear penalty and emit search.reboosted on stock.replenished")
    void should_reboost_when_stockReplenishedReceived() throws Exception {
        ProductDocument doc = ProductDocument.builder().id("100").name("Phone").build();
        when(productSearchRepository.findById("100")).thenReturn(Optional.of(doc));

        StockReplenishedEvent event = new StockReplenishedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 50, Instant.now());
        listener.onStockReplenished(objectMapper.writeValueAsString(event));

        verify(productSearchRepository).save(doc);
        verify(kafkaTemplate).send(eq("search.reboosted.due-to-stock"), anyString(), any(SearchReboostedEvent.class));
    }

    @Test
    @DisplayName("should drop duplicate events (idempotency)")
    void should_dropDuplicate_when_sameEventReceivedTwice() throws Exception {
        ProductDocument doc = ProductDocument.builder().id("100").build();
        when(productSearchRepository.findById("100")).thenReturn(Optional.of(doc));

        UUID eventId = UUID.randomUUID();
        StockLowDetectedEvent event = new StockLowDetectedEvent(
                eventId, 100L, "SKU-100", 2, 5, Instant.now());
        String payload = objectMapper.writeValueAsString(event);

        listener.onStockLow(payload);
        listener.onStockLow(payload);

        verify(productSearchRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should swallow malformed payload to avoid blocking the partition")
    void should_swallowError_when_messageMalformed() {
        listener.onStockLow("garbage");
        verifyNoInteractions(productSearchRepository);
        verifyNoInteractions(kafkaTemplate);
    }
}
