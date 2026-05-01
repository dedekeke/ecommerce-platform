package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.dto.CartDto;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoHelpersTest {

    @Test
    void should_returnInStockTrue_when_inStockFlagIsTrue() {
        var p = new ProductDto("1", "n", null, null, null, null, null, 0, true);
        assertThat(p.resolvedInStock()).isTrue();
    }

    @Test
    void should_deriveInStock_when_flagNullButQuantityPositive() {
        var p = new ProductDto("1", "n", null, null, null, null, null, 3, null);
        assertThat(p.resolvedInStock()).isTrue();
    }

    @Test
    void should_returnFalse_when_flagAndQuantityBothMissing() {
        var p = new ProductDto("1", "n", null, null, null, null, null, null, null);
        assertThat(p.resolvedInStock()).isFalse();
    }

    @Test
    void should_returnEmptyLists_when_cartHasNullCollections() {
        var c = new CartDto("u", null, null, null, null);
        assertThat(c.safeItems()).isEmpty();
        assertThat(c.safePromotions()).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_orderHasNullItems() {
        var o = new OrderDto("o", "ord", "u", "PAID", null, null, "t");
        assertThat(o.safeItems()).isEmpty();
    }

    @Test
    void should_returnEmptyPage_when_emptyFactoryUsed() {
        PageDto<String> p = PageDto.empty();
        assertThat(p.content()).isEmpty();
        assertThat(p.totalElements()).isZero();
        assertThat(p.totalPages()).isZero();
        assertThat(p.number()).isZero();
    }

    @Test
    void should_holdContent_when_constructed() {
        PageDto<String> p = new PageDto<>(List.of("a"), 1L, 1, 0);
        assertThat(p.content()).containsExactly("a");
    }
}
