package com.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the production {@code application.yml} against re-introducing the
 * boot-time Auth0 dependency that crashed the gateway (Lore bug 1b8953dc).
 *
 * <p>The gateway is a JWT resource server, not an OAuth2 login client. An
 * {@code spring.security.oauth2.client} registration with a provider
 * {@code issuer-uri} makes Spring perform blocking OIDC discovery during boot,
 * so a transient Auth0 outage crashes startup. The {@code TokenRelay}
 * default-filter is what dragged that client config in (it relays a token from
 * an {@code OAuth2AuthorizedClient}, which a resource-server principal never
 * has — a no-op here). Both were removed; this fast, context-free test keeps
 * them out so the heavier {@link UnreachableIssuerBootTest} can't be the only
 * thing standing between us and a regression.
 */
class NoOAuth2ClientConfigTest {

    private PropertySource<?> load() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).isNotEmpty();
        return sources.get(0);
    }

    @Test
    void should_notConfigureAnyOAuth2ClientRegistration() throws IOException {
        PropertySource<?> src = load();
        assertThat(src.getProperty("spring.security.oauth2.client.registration.auth0.client-id"))
                .as("no OAuth2 client registration — it triggers boot-time OIDC discovery (bug 1b8953dc)")
                .isNull();
        assertThat(src.getProperty("spring.security.oauth2.client.provider.auth0.issuer-uri"))
                .as("no OAuth2 client provider issuer-uri — it triggers boot-time OIDC discovery")
                .isNull();
    }

    @Test
    void should_keepResourceServerJwtConfig() throws IOException {
        assertThat(load().getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .as("resource-server JWT validation must remain configured")
                .isNotNull();
    }

    @Test
    void should_notUseTokenRelayDefaultFilter() throws IOException {
        PropertySource<?> src = load();
        // default-filters is a YAML list; assert none of its entries is TokenRelay.
        for (int i = 0; i < 16; i++) {
            Object scalar = src.getProperty("spring.cloud.gateway.default-filters[" + i + "]");
            Object named = src.getProperty("spring.cloud.gateway.default-filters[" + i + "].name");
            assertThat(String.valueOf(scalar)).isNotEqualTo("TokenRelay");
            assertThat(String.valueOf(named)).isNotEqualTo("TokenRelay");
        }
    }
}
