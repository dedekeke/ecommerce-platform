package com.ecommerce.searchservice.service;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.AutocompleteResponse;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies autocomplete uses the ES-capped ({@code Top10}) repository query
 * rather than fetching an unbounded hit set and trimming in Java.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductSearchService — autocomplete bounded query")
class ProductSearchServiceAutocompleteTest {

    @Mock private ProductSearchRepository repository;
    @Mock private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks private ProductSearchService service;

    @Test
    @DisplayName("should_useTop10CappedQuery_when_autocompleteRequested")
    void should_useTop10CappedQuery_when_autocompleteRequested() {
        when(repository.findTop10ByNameAutocompleteContaining("app"))
                .thenReturn(List.of(product("Apple"), product("Apricot")));

        service.autocomplete("app");

        verify(repository).findTop10ByNameAutocompleteContaining("app");
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("should_returnDistinctNames_when_duplicatesPresent")
    void should_returnDistinctNames_when_duplicatesPresent() {
        when(repository.findTop10ByNameAutocompleteContaining("app"))
                .thenReturn(List.of(product("Apple"), product("Apple"), product("Apricot")));

        AutocompleteResponse response = service.autocomplete("app");

        assertThat(response.getSuggestions()).containsExactly("Apple", "Apricot");
    }

    private ProductDocument product(String name) {
        return ProductDocument.builder().name(name).build();
    }
}
