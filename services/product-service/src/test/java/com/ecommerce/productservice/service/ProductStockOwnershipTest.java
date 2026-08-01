package com.ecommerce.productservice.service;

import com.ecommerce.productservice.event.ProductEventPublisher;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ownership contract for {@code Product.stockQuantity}.
 *
 * <p>Stock movement is owned by inventory-service (reservations under
 * pessimistic locking, restock/restore, reorder alerts). product-service holds
 * only a catalog DISPLAY snapshot used by the in-stock listing filters. The
 * general product PUT therefore MUST NOT move the number: its payload carries a
 * snapshot read when the admin form was opened, so writing it back would blindly
 * clobber any stock change made in between (lost update).
 *
 * <p>Stock is seeded on create and changed afterwards ONLY through the dedicated
 * {@code PATCH /api/products/{id}/stock} endpoint, which expresses that intent
 * explicitly.
 */
@ExtendWith(MockitoExtension.class)
class ProductStockOwnershipTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductEventPublisher eventPublisher;

    @Mock
    private ProductListingCache listingCache;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private static final long PRODUCT_ID = 42L;
    private static final int STORED_STOCK = 50;

    private Product stored;

    @BeforeEach
    void setUp() {
        stored = Product.builder()
                .id(PRODUCT_ID)
                .sku("SKU-42")
                .name("Stored name")
                .description("Stored description")
                .price(new BigDecimal("10.00"))
                .currency("USD")
                .stockQuantity(STORED_STOCK)
                .active(true)
                .build();
    }

    private static Product incomingDetails(int stockQuantity) {
        return Product.builder()
                .sku("SKU-42")
                .name("New name")
                .description("New description")
                .price(new BigDecimal("19.99"))
                .currency("EUR")
                .stockQuantity(stockQuantity)
                .active(false)
                .build();
    }

    private Product captureSaved() {
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "incoming stock={0}")
    @ValueSource(ints = {0, 1, 999})
    void should_preserveStoredStock_when_updateProductCarriesAnyStockValue(int incomingStock) {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(stored));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(PRODUCT_ID, incomingDetails(incomingStock));

        assertThat(captureSaved().getStockQuantity()).isEqualTo(STORED_STOCK);
    }

    @Test
    void should_updateEditableFields_when_updateProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(stored));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(PRODUCT_ID, incomingDetails(999));

        Product saved = captureSaved();
        assertThat(saved.getName()).isEqualTo("New name");
        assertThat(saved.getDescription()).isEqualTo("New description");
        assertThat(saved.getPrice()).isEqualByComparingTo("19.99");
        assertThat(saved.getCurrency()).isEqualTo("EUR");
        assertThat(saved.getActive()).isFalse();
    }

    @Test
    void should_publishUpdatedEventWithStoredStock_when_updateProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(stored));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(PRODUCT_ID, incomingDetails(999));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(eventPublisher).publishProductUpdated(captor.capture());
        assertThat(captor.getValue().getStockQuantity()).isEqualTo(STORED_STOCK);
    }

    @Test
    void should_setStock_when_dedicatedStockEndpointUsed() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(stored));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateStockQuantity(PRODUCT_ID, 7);

        assertThat(result.getStockQuantity()).isEqualTo(7);
        assertThat(captureSaved().getStockQuantity()).isEqualTo(7);
    }

    @Test
    void should_notPublishProductUpdatedEvent_when_onlyStockChanges() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(stored));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateStockQuantity(PRODUCT_ID, 7);

        verify(eventPublisher, never()).publishProductUpdated(any(Product.class));
    }

    @Test
    void should_rejectNegativeQuantity_when_updateStockQuantity() {
        assertThatThrownBy(() -> productService.updateStockQuantity(PRODUCT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void should_seedStock_when_createProduct() {
        Product incoming = incomingDetails(25);
        when(productRepository.findBySku("SKU-42")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = productService.createProduct(incoming);

        assertThat(created.getStockQuantity()).isEqualTo(25);
    }
}
