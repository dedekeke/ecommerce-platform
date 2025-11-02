# Common Security Package

This package provides reusable security components for all microservices in the e-commerce platform.

## Package Structure

```
com.ecommerce.common.security/
├── annotation/          # Custom security annotations
│   ├── IsAdmin.java
│   ├── IsAuthenticated.java
│   ├── IsOwner.java
│   ├── RequiresPermission.java
│   └── RequiresScope.java
├── config/             # Security configuration classes
│   ├── Auth0ClientCredentialsConfig.java
│   ├── BaseSecurityConfig.java
│   └── M2MRestTemplateConfig.java
├── constants/          # Security constants
│   └── SecurityConstants.java
├── evaluator/          # Custom security evaluators
│   └── OwnershipEvaluator.java
├── interceptor/        # HTTP interceptors
│   └── M2MAuthenticationInterceptor.java
├── model/              # Security models
│   └── M2MToken.java
├── service/            # Security services
│   ├── CachedM2MAuthenticationService.java
│   └── M2MAuthenticationService.java
├── util/               # Utility classes
│   ├── JwtClaimsExtractor.java
│   └── SecurityContextUtil.java
└── validator/          # Custom validators
    └── AudienceValidator.java
```

## Core Components

### BaseSecurityConfig

The base security configuration that all services should extend. Provides:
- JWT decoder with audience validation
- JWT authentication converter
- Default security filter chain

**Usage:**
```java
@Configuration
public class MyServiceSecurityConfig extends BaseSecurityConfig {
    // Override methods as needed
}
```

### JwtClaimsExtractor

Utility for extracting claims from JWT tokens.

**Common methods:**
- `getCurrentUserId()` - Get current user's ID
- `getCurrentEmail()` - Get current user's email
- `getScopes(jwt)` - Get token scopes
- `getPermissions(jwt)` - Get token permissions
- `hasScope(jwt, scope)` - Check if token has scope
- `hasPermission(jwt, permission)` - Check if token has permission

### SecurityContextUtil

Utility for working with Spring Security Context.

**Common methods:**
- `isAuthenticated()` - Check if user is authenticated
- `getCurrentUserId()` - Get current user ID from context
- `isAdmin()` - Check if user is admin
- `hasScope(scope)` - Check if current user has scope
- `hasAuthority(authority)` - Check if user has authority

### M2M Authentication

Services for machine-to-machine authentication:

**M2MAuthenticationService:**
- `getAccessToken()` - Get M2M access token from Auth0
- `getAccessTokenWithScopes(scopes)` - Get token with specific scopes

**CachedM2MAuthenticationService:**
- Same methods as above, but with Redis caching
- `invalidateCache()` - Manually clear cached tokens

### Annotations

Custom annotations for authorization:

- `@IsAuthenticated` - Require authentication
- `@IsAdmin` - Require admin role
- `@IsOwner` - Require resource ownership or admin
- `@RequiresScope(value)` - Require specific OAuth2 scope
- `@RequiresPermission(value)` - Require specific permission

## Quick Start

### 1. Service Configuration

Create security config in your service:

```java
@Configuration
@EnableWebSecurity
public class ProductServiceSecurityConfig extends BaseSecurityConfig {

    @Override
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("write:products")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }
}
```

### 2. Controller with Annotations

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping
    public List<Product> getProducts() {
        // Public - no authentication required
        return productService.getProducts();
    }

    @PostMapping
    @IsAuthenticated
    public Product createProduct(@RequestBody Product product) {
        // Requires authentication
        return productService.createProduct(product);
    }

    @DeleteMapping("/{id}")
    @IsAdmin
    public void deleteProduct(@PathVariable Long id) {
        // Only admins can delete
        productService.deleteProduct(id);
    }
}
```

### 3. Service with Ownership Check

```java
@Service
public class OrderService {

    public Order getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        String currentUserId = SecurityContextUtil.getCurrentUserId();

        // Check if user owns the order or is admin
        if (!order.getUserId().equals(currentUserId) && !SecurityContextUtil.isAdmin()) {
            throw new ForbiddenException("Access denied");
        }

        return order;
    }
}
```

### 4. M2M Service Communication

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CachedM2MAuthenticationService m2mAuth;
    private final RestTemplate restTemplate;

    public void notifyInventory(Order order) {
        String authHeader = m2mAuth.getAuthorizationHeader();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", authHeader);

        HttpEntity<InventoryRequest> request = new HttpEntity<>(inventoryRequest, headers);

        restTemplate.exchange(
            "http://inventory-service/api/v1/inventory/reserve",
            HttpMethod.POST,
            request,
            InventoryResponse.class
        );
    }
}
```

## Configuration Properties

Add to your `application.yml`:

```yaml
auth0:
  domain: ${AUTH0_DOMAIN}
  audience: ${AUTH0_AUDIENCE}

  # M2M configuration
  m2m:
    domain: ${AUTH0_DOMAIN}
    client-id: ${AUTH0_M2M_CLIENT_ID}
    client-secret: ${AUTH0_M2M_CLIENT_SECRET}
    audience: ${AUTH0_AUDIENCE}
```

## Testing

Use test helpers for security testing:

```java
@SpringBootTest
public class ProductControllerTest {

    @Test
    @WithMockJwt(
        subject = "auth0|test-user",
        scopes = {"read:products"},
        email = "test@example.com"
    )
    public void testGetProducts() {
        // Test with mock JWT
    }

    @Test
    public void testWithCustomJwt() {
        Jwt jwt = JwtTestHelper.createJwtWithScopes(
            "auth0|user-123",
            List.of("read:products", "write:products")
        );

        SecurityContextTestHelper.setupSecurityContext(jwt);

        // Run test

        SecurityContextTestHelper.clearSecurityContext();
    }
}
```

## Security Constants

Use predefined constants instead of hardcoded strings:

```java
import static com.ecommerce.common.security.constants.SecurityConstants.*;

@Service
public class ProductService {

    @PreAuthorize("hasAuthority(PERMISSION_WRITE_PRODUCTS)")
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
}
```

## See Also

- [SECURITY_GUIDE.md](../../SECURITY_GUIDE.md) - Comprehensive security guide
- [Auth0 Documentation](https://auth0.com/docs)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
