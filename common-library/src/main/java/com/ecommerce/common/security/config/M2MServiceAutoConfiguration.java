package com.ecommerce.common.security.config;

import com.ecommerce.common.security.service.CachedM2MAuthenticationService;
import com.ecommerce.common.security.service.M2MAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Registers {@link CachedM2MAuthenticationService} only when its Redis and M2M
 * collaborators are present. Previously a {@code @Service}, it broke sliced
 * tests that scan {@code com.ecommerce.common} without a RedisTemplate.
 */
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
public class M2MServiceAutoConfiguration {

    @Bean
    @ConditionalOnBean({M2MAuthenticationService.class, RedisTemplate.class})
    @ConditionalOnMissingBean(CachedM2MAuthenticationService.class)
    public CachedM2MAuthenticationService cachedM2MAuthenticationService(
            M2MAuthenticationService m2mAuthenticationService,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        return new CachedM2MAuthenticationService(m2mAuthenticationService, redisTemplate, objectMapper);
    }
}
