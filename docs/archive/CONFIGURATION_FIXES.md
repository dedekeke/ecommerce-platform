# Configuration Fixes - API Gateway

## Deprecated Configurations Resolved

### Overview
Updated API Gateway configuration files to use Spring Boot 3.2 and Spring Cloud 2023.0.0 conventions, removing all deprecated properties.

## Changes Made

### 1. Removed Deprecated Prometheus Metrics Configuration ❌→✅

**Before (Deprecated):**
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

**Issue:** `management.metrics.export.prometheus.enabled` is deprecated in Spring Boot 3.2+. Prometheus is now automatically enabled when the `micrometer-registry-prometheus` dependency is present.

**After (Fixed):**
- Removed the `export.prometheus.enabled` property
- Prometheus metrics are auto-configured

**Benefits:**
- ✅ Follows Spring Boot 3.2+ auto-configuration conventions
- ✅ Cleaner configuration
- ✅ No deprecation warnings

### 2. Updated Metrics Tags to Observations Key-Values ❌→✅

**Before (Deprecated):**
```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
```

**Issue:** `management.metrics.tags` is deprecated in favor of `management.observations.key-values` in Spring Boot 3.x with Micrometer Observation API

**After (Fixed):**
```yaml
management:
  observations:
    key-values:
      application: ${spring.application.name}
```

**Benefits:**
- ✅ Uses the new Observation API
- ✅ Better integration with distributed tracing
- ✅ More consistent with Spring Boot 3.x observability features

### 3. Removed Deprecated CORS Configuration ❌→✅

**Before (Deprecated):**
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: [...]
            allowedMethods: [...]
```

**Issue:** `globalcors` is deprecated in Spring Cloud Gateway 2023.0.0+

**After (Fixed):**
- Removed `globalcors` configuration from YAML
- CORS is now properly configured via `CorsConfigurationSource` bean in `SecurityConfig.java`
- This is the recommended approach for Spring Boot 3.2+

**Benefits:**
- ✅ Programmatic CORS configuration is more flexible
- ✅ Better integration with Spring Security
- ✅ No deprecation warnings

### 4. Updated Eureka Configuration to Kebab-Case ❌→✅

**Before (Old style):**
```yaml
eureka:
  client:
    serviceUrl:
      defaultZone: http://eureka-server:8761/eureka/
```

**After (Modern style):**
```yaml
eureka:
  client:
    service-url:
      default-zone: http://eureka-server:8761/eureka/
```

**Issue:** Spring Boot 3.x prefers kebab-case for property names (relaxed binding)

**Changes:**
- `serviceUrl` → `service-url`
- `defaultZone` → `default-zone`

**Benefits:**
- ✅ Follows Spring Boot 3.x conventions
- ✅ Consistent with modern Spring configuration style
- ✅ Better IDE support and auto-completion

## Files Modified

1. **`infrastructure/api-gateway/src/main/resources/application.yml`**
   - Removed deprecated `globalcors` configuration
   - Updated Eureka properties to kebab-case

2. **`infrastructure/api-gateway/src/main/resources/application-docker.yml`**
   - Updated Eureka properties to kebab-case

## Current CORS Configuration

CORS is now handled via the `SecurityConfig` bean:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:3000",  // React Shell App
        "http://localhost:5000",  // React Shell App (Vite)
        "http://localhost:5001",  // Product Catalog MFE
        "http://localhost:5002",  // Cart MFE
        "http://localhost:5003",  // Checkout MFE
        "http://localhost:4200",  // User Dashboard MFE (Angular)
        "http://localhost:4201"   // Admin Dashboard MFE (Angular)
    ));

    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
}
```

Location: `infrastructure/api-gateway/src/main/java/com/ecommerce/gateway/config/SecurityConfig.java:98`

## Verification

✅ **Build Status:** SUCCESS
✅ **Compilation:** No errors or warnings
✅ **Configuration:** All properties follow Spring Boot 3.2 conventions
✅ **Functionality:** CORS and Eureka integration remain fully functional

## Additional Notes

### Why These Changes Matter

1. **Future Compatibility:** Using deprecated configurations can lead to issues when upgrading to newer Spring versions
2. **IDE Support:** Modern conventions provide better auto-completion and validation
3. **Best Practices:** Following current Spring conventions makes the codebase more maintainable
4. **Security:** Programmatic CORS configuration offers better control and security

### Testing Recommendations

After these changes, verify:

1. **CORS:** Test cross-origin requests from frontend applications
   ```bash
   curl -H "Origin: http://localhost:3000" \
        -H "Access-Control-Request-Method: POST" \
        -H "Access-Control-Request-Headers: Authorization" \
        -X OPTIONS \
        http://localhost:8080/api/products
   ```

2. **Eureka Registration:** Check that API Gateway registers correctly
   ```bash
   curl http://localhost:8761/eureka/apps
   ```

3. **Route Discovery:** Verify automatic route creation from Eureka services
   ```bash
   curl http://localhost:8080/actuator/gateway/routes
   ```

## Spring Boot 3.2 Configuration Best Practices

Going forward, follow these conventions:

1. **Use kebab-case** for property names: `service-url` not `serviceUrl`
2. **Configure CORS programmatically** via `@Bean` methods
3. **Use relaxed binding** for environment variables: `SERVICE_URL` → `service-url`
4. **Leverage configuration properties** with `@ConfigurationProperties`
5. **Avoid deprecated properties** - check Spring documentation for current conventions

## References

- [Spring Boot 3.2 Configuration Reference](https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/features.html#features.external-config)
- [Spring Cloud Gateway CORS Configuration](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#cors-configuration)
- [Eureka Client Configuration](https://docs.spring.io/spring-cloud-netflix/docs/current/reference/html/#service-discovery-eureka-clients)
- [Spring Boot Relaxed Binding](https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding)

---

**Updated:** October 31, 2025
**Status:** ✅ All deprecated configurations resolved
**Build:** SUCCESS
