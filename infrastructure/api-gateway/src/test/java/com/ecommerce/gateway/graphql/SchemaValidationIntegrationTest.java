package com.ecommerce.gateway.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full gateway context with security disabled and verifies that
 * Spring for GraphQL parsed and validated the schema without errors.
 *
 * <p>Spring for GraphQL fails fast on:
 * <ul>
 *   <li>missing field types,</li>
 *   <li>fields declared in {@code @SchemaMapping} but absent from the schema,</li>
 *   <li>controller methods whose return types cannot be coerced.</li>
 * </ul>
 * If this test passes, the BFF wiring is structurally sound.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "request-logging.enabled=false",
        "security.ip-whitelist.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.tracing.enabled=false",
        "AUTH0_ISSUER_URI=http://localhost/issuer",
        "AUTH0_DOMAIN=localhost",
        "AUTH0_AUDIENCE=test",
        "AUTH0_CLIENT_ID=test",
        "AUTH0_CLIENT_SECRET=test",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "EUREKA_URI=http://localhost:8761/eureka",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
})
class SchemaValidationIntegrationTest {

    @Autowired
    private GraphQlSource graphQlSource;

    @Test
    void should_validateSchema_when_contextStarts() {
        assertThat(graphQlSource).isNotNull();
        assertThat(graphQlSource.schema()).isNotNull();
        assertThat(graphQlSource.schema().getQueryType().getFieldDefinitions())
                .extracting(f -> f.getName())
                .contains("product", "products", "recommendations", "cart", "order", "myOrders");
    }
}
