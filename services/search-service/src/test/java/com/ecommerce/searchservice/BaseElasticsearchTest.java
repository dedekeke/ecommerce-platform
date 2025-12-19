package com.ecommerce.searchservice;

import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base test class for Elasticsearch integration tests using Testcontainers
 */
@DataElasticsearchTest
@Testcontainers
public abstract class BaseElasticsearchTest {

    @Container
    static ElasticsearchContainer elasticsearchContainer = 
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearchContainer::getHttpHostAddress);
    }
}
