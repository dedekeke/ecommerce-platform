package com.ecommerce.searchservice.admin;

import com.ecommerce.common.featureflag.FeatureFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer security tests for {@link BackfillController}.
 *
 * <p>Proves the {@code @PreAuthorize} admin guard is actually enforced. Loads
 * only the controller + a method-secured filter chain (mirroring production's
 * {@code SecurityConfig} but with a mocked {@link JwtDecoder}, so no Auth0
 * network call happens and the ES repositories on the application class are not
 * bootstrapped). Authentication is injected via {@code .with(jwt())}.
 */
@SpringJUnitWebConfig(classes = {
        BackfillController.class,
        BackfillControllerSecurityTest.TestSecurityConfig.class
})
class BackfillControllerSecurityTest {

    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {

        @Bean
        BrandRatingBackfillService backfillService() {
            return mock(BrandRatingBackfillService.class);
        }

        @Bean
        FeatureFlags featureFlags() {
            return mock(FeatureFlags.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(new AntPathRequestMatcher("/api/admin/**")).authenticated()
                            .anyRequest().permitAll())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
            return http.build();
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BrandRatingBackfillService backfillService;

    @Autowired
    private FeatureFlags featureFlags;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(backfillService, featureFlags);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/search/backfill/brand-rating"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return403_when_authenticatedWithoutAdminScope() throws Exception {
        mockMvc.perform(post("/api/admin/search/backfill/brand-rating")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_read:search"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_allow_when_adminScope_andFlagEnabled() throws Exception {
        when(featureFlags.isEnabled(BackfillController.FLAG_ES_BACKFILL)).thenReturn(true);
        when(backfillService.backfill()).thenReturn(7L);

        mockMvc.perform(post("/api/admin/search/backfill/brand-rating")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk());
    }
}
