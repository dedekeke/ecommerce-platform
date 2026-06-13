package com.ecommerce.promotionservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link UserServiceClient} against a WireMock-stubbed user-service.
 */
@DisplayName("UserServiceClient")
class UserServiceClientTest {

    private WireMockServer wireMock;
    private UserServiceClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new UserServiceClient(
                RestClient.builder(), "http://localhost:" + wireMock.port(), 2000, 3000);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should_resolveContact_when_userExists")
    void should_resolveContact_when_userExists() {
        wireMock.stubFor(get(urlEqualTo("/api/users/by-auth0/auth0%7Calice"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"1","email":"alice@example.com","fullName":"Alice A"}
                                """)));

        Optional<UserContact> contact = client.findByAuth0Id("auth0|alice");

        assertThat(contact).isPresent();
        assertThat(contact.get().email()).isEqualTo("alice@example.com");
        assertThat(contact.get().fullName()).isEqualTo("Alice A");
    }

    @Test
    @DisplayName("should_returnEmpty_when_userNotFound")
    void should_returnEmpty_when_userNotFound() {
        wireMock.stubFor(get(urlEqualTo("/api/users/by-auth0/auth0%7Cghost"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.findByAuth0Id("auth0|ghost")).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_serviceErrors")
    void should_returnEmpty_when_serviceErrors() {
        wireMock.stubFor(get(urlEqualTo("/api/users/by-auth0/auth0%7Cbob"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(client.findByAuth0Id("auth0|bob")).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_subIsBlank")
    void should_returnEmpty_when_subIsBlank() {
        assertThat(client.findByAuth0Id("  ")).isEmpty();
        assertThat(client.findByAuth0Id(null)).isEmpty();
    }

    @Test
    @DisplayName("should_forwardBearerToken_when_jwtInSecurityContext")
    void should_forwardBearerToken_when_jwtInSecurityContext() {
        Jwt jwt = Jwt.withTokenValue("the-access-token")
                .header("alg", "RS256")
                .subject("auth0|caller")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt, jwt, "SCOPE_admin"));

        wireMock.stubFor(get(urlEqualTo("/api/users/by-auth0/auth0%7Calice"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"email\":\"alice@example.com\",\"fullName\":\"Alice A\"}")));

        client.findByAuth0Id("auth0|alice");

        wireMock.verify(getRequestedFor(urlEqualTo("/api/users/by-auth0/auth0%7Calice"))
                .withHeader("Authorization", equalTo("Bearer the-access-token")));
    }

    @Test
    @DisplayName("should_sendNoAuthHeader_when_noJwtInContext")
    void should_sendNoAuthHeader_when_noJwtInContext() {
        SecurityContextHolder.clearContext();
        wireMock.stubFor(get(urlEqualTo("/api/users/by-auth0/auth0%7Calice"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"email\":\"alice@example.com\",\"fullName\":\"Alice A\"}")));

        client.findByAuth0Id("auth0|alice");

        wireMock.verify(getRequestedFor(urlEqualTo("/api/users/by-auth0/auth0%7Calice"))
                .withoutHeader("Authorization"));
    }
}
