package com.ecommerce.paymentservice.customer;

import com.ecommerce.paymentservice.config.StripeProperties;
import com.ecommerce.paymentservice.config.StripeRequestOptionsFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Integration test exercising the real Stripe Java SDK against a WireMock-stubbed Stripe API.
 * No real Stripe credentials or network calls are involved; the repository is mocked so the test
 * stays focused on the get-or-create resolution logic and the Stripe customer binding.
 */
@DisplayName("StripeCustomerService Integration Tests (WireMock)")
class StripeCustomerServiceTest {

    private WireMockServer wireMock;
    private StripeCustomerRepository repository;
    private StripeCustomerService service;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        StripeProperties props = new StripeProperties();
        props.setSecretKey("sk_test_dummy");
        props.setApiBase("http://localhost:" + wireMock.port());

        StripeRequestOptionsFactory factory = new StripeRequestOptionsFactory(props);
        repository = mock(StripeCustomerRepository.class);
        service = new StripeCustomerService(repository, factory);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("should create a Stripe customer and persist the mapping on first use")
    void should_createAndPersist_when_noMappingExists() {
        when(repository.findByUserId("user-1")).thenReturn(Optional.empty());
        wireMock.stubFor(post(urlPathEqualTo("/v1/customers"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"cus_test_1","object":"customer","metadata":{"userId":"user-1"}}""")));

        String customerId = service.getOrCreateCustomerId("user-1");

        assertThat(customerId).isEqualTo("cus_test_1");
        // The owning user is stamped on the Stripe customer metadata.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/customers"))
                .withRequestBody(containing("metadata"))
                .withRequestBody(containing("user-1"))
                .withHeader("Authorization", equalTo("Bearer sk_test_dummy")));

        ArgumentCaptor<StripeCustomer> saved = ArgumentCaptor.forClass(StripeCustomer.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("user-1");
        assertThat(saved.getValue().getCustomerId()).isEqualTo("cus_test_1");
    }

    @Test
    @DisplayName("should reuse the stored customer id without calling Stripe")
    void should_reuseStoredId_when_mappingExists() {
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(StripeCustomer.builder()
                .userId("user-1")
                .customerId("cus_existing")
                .build()));

        String customerId = service.getOrCreateCustomerId("user-1");

        assertThat(customerId).isEqualTo("cus_existing");
        verify(repository, never()).save(any());
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/v1/customers")));
    }

    @Test
    @DisplayName("should return null for a blank userId without any Stripe call or save")
    void should_returnNull_when_userIdBlank() {
        String customerId = service.getOrCreateCustomerId("   ");

        assertThat(customerId).isNull();
        verifyNoInteractions(repository);
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/v1/customers")));
    }
}
