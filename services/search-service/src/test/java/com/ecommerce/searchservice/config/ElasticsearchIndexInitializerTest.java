package com.ecommerce.searchservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexInitializerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private BooleanResponse booleanResponse;

    private ElasticsearchIndexInitializer indexInitializer;

    @BeforeEach
    void setUp() {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        indexInitializer = new ElasticsearchIndexInitializer(elasticsearchClient);
    }

    @Test
    void shouldCreateIndexWhenNotExists() throws Exception {
        // Given
        when(booleanResponse.value()).thenReturn(false);
        when(indicesClient.exists(any(Function.class))).thenReturn(booleanResponse);

        // When
        indexInitializer.initializeIndex();

        // Then
        verify(indicesClient).exists(any(Function.class));
        verify(indicesClient).create(any(Function.class));
    }

    @Test
    void shouldNotCreateIndexWhenExists() throws Exception {
        // Given
        when(booleanResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(Function.class))).thenReturn(booleanResponse);

        // When
        indexInitializer.initializeIndex();

        // Then
        verify(indicesClient).exists(any(Function.class));
        verify(indicesClient, never()).create(any(Function.class));
    }

    @Test
    void shouldHandleIOExceptionGracefully() throws Exception {
        // Given
        when(indicesClient.exists(any(Function.class)))
                .thenThrow(new IOException("Connection error"));

        // When/Then - should not throw exception
        indexInitializer.initializeIndex();

        verify(indicesClient).exists(any(Function.class));
    }
}
