# Security Configuration Guide

This guide explains how to use the common security configuration in all microservices.

## Table of Contents

1. [Overview](#overview)
2. [Basic Setup](#basic-setup)
3. [JWT Authentication](#jwt-authentication)
4. [Authorization](#authorization)
5. [M2M Authentication](#m2m-authentication)
6. [Testing](#testing)
7. [Best Practices](#best-practices)

---

## Overview

The common-library provides a comprehensive security framework based on:
- **Auth0** for authentication and authorization
- **JWT** tokens for stateless authentication
- **OAuth2** scopes and permissions for fine-grained access control
- **M2M** (Machine-to-Machine) authentication for service-to-service communication
- **Redis** caching for M2M tokens

---

## Basic Setup

### 1. Extend BaseSecurityConfig

In your microservice, create a security configuration that extends `BaseSecurityConfig`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends BaseSecurityConfig {

    @Override
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/products/**").permitAll()  // Public product browsing

                // Protected endpoints
                .requestMatchers("/api/v1/orders/**").authenticated()
                .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                // Require authentication for all other requests
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

### 2. Configure Auth0 Properties

Add these properties to your `application.yml`:

```yaml
auth0:
  domain: ${AUTH0_DOMAIN:your-tenant.auth0.com}
  audience: ${AUTH0_AUDIENCE:https://api.ecommerce.com}

  # For M2M authentication (service-to-service)
  m2m:
    domain: ${AUTH0_DOMAIN:your-tenant.auth0.com}
    client-id: ${AUTH0_M2M_CLIENT_ID}
    client-secret: ${AUTH0_M2M_CLIENT_SECRET}
    audience: ${AUTH0_AUDIENCE:https://api.ecommerce.com}
```

---

## JWT Authentication

### Extracting User Information

Use `JwtClaimsExtractor` to extract information from JWT tokens:

```java
@Service
public class UserService {

    public UserProfile getCurrentUserProfile() {
        // Get current user ID from JWT
        String userId = JwtClaimsExtractor.getCurrentUserId();
        String email = JwtClaimsExtractor.getCurrentEmail();

        // ... fetch user profile from database
        return userProfile;
    }

    public void processOrder(Order order) {
        // Get JWT from security context
        Jwt jwt = SecurityContextUtil.getCurrentJwt()
            .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        String userId = JwtClaimsExtractor.getUserId(jwt);
        String email = JwtClaimsExtractor.getEmail(jwt);

        // ... process order
    }
}
```

### Available Extraction Methods

```java
// User information
String userId = JwtClaimsExtractor.getCurrentUserId();
String email = JwtClaimsExtractor.getCurrentEmail();

// From specific JWT
String name = JwtClaimsExtractor.getName(jwt);
String picture = JwtClaimsExtractor.getPicture(jwt);

// Scopes and permissions
List<String> scopes = JwtClaimsExtractor.getScopes(jwt);
List<String> permissions = JwtClaimsExtractor.getPermissions(jwt);

boolean hasScope = JwtClaimsExtractor.hasScope(jwt, "read:products");
boolean hasPermission = JwtClaimsExtractor.hasPermission(jwt, "delete:products");

// Custom claims
String customClaim = JwtClaimsExtractor.getClaimAsString(jwt, "custom_claim");
Map<String, Object> metadata = JwtClaimsExtractor.getAppMetadata(jwt, "https://your-app.com/");
```

---

## Authorization

### Using Annotations

The library provides convenient annotations for authorization:

#### @IsAuthenticated

```java
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    @GetMapping
    @IsAuthenticated
    public UserProfile getProfile() {
        // Only authenticated users can access
        return profileService.getCurrentUserProfile();
    }
}
```

#### @IsAdmin

```java
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @PostMapping("/products")
    @IsAdmin
    public Product createProduct(@RequestBody Product product) {
        // Only admins can access
        return productService.createProduct(product);
    }
}
```

#### @RequiresScope

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/{id}")
    @RequiresScope("read:orders")
    public Order getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @PostMapping
    @RequiresScope("write:orders")
    public Order createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }
}
```

#### @IsOwner

```java
@GetMapping("/{id}")
@IsOwner
public Order getOrder(@PathVariable("id") Long id) {
    // Only the owner or admin can access
    return orderService.getOrder(id);
}
```

### Using SecurityContextUtil

```java
@Service
public class OrderService {

    public List<Order> getUserOrders() {
        // Check if user is authenticated
        if (!SecurityContextUtil.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        // Get current user ID
        String userId = SecurityContextUtil.getCurrentUserId();

        // Check if user is admin
        if (SecurityContextUtil.isAdmin()) {
            return orderRepository.findAll(); // Admin sees all orders
        }

        return orderRepository.findByUserId(userId); // User sees only their orders
    }

    public void deleteOrder(Long orderId) {
        // Check for specific authority
        if (!SecurityContextUtil.hasAuthority("delete:orders")) {
            throw new ForbiddenException("Insufficient permissions");
        }

        orderRepository.deleteById(orderId);
    }
}
```

### Programmatic Authorization

```java
@Service
public class ProductService {

    public void updateProduct(Long productId, ProductUpdateRequest request) {
        // Check multiple authorities
        if (!SecurityContextUtil.hasAnyAuthority("write:products", "admin:access")) {
            throw new ForbiddenException("Insufficient permissions");
        }

        // ... update product
    }

    public void deleteProduct(Long productId) {
        // Check all authorities required
        if (!SecurityContextUtil.hasAllAuthorities("delete:products", "admin:access")) {
            throw new ForbiddenException("Admin access required");
        }

        // ... delete product
    }
}
```

---

## M2M Authentication

### Basic Usage

Use `CachedM2MAuthenticationService` for service-to-service communication:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CachedM2MAuthenticationService m2mAuthService;
    private final RestTemplate restTemplate;

    public void notifyPaymentService(Order order) {
        // Get M2M token (cached in Redis)
        String authHeader = m2mAuthService.getAuthorizationHeader();

        // Make request to Payment Service
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", authHeader);

        HttpEntity<PaymentRequest> request = new HttpEntity<>(paymentRequest, headers);

        restTemplate.exchange(
            "http://payment-service/api/v1/payments",
            HttpMethod.POST,
            request,
            PaymentResponse.class
        );
    }
}
```

### Using M2M Interceptor

Automatically add M2M tokens to all requests:

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplateWithM2MAuth(M2MAuthenticationInterceptor interceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(interceptor));
        return restTemplate;
    }
}
```

### Token Caching

M2M tokens are automatically cached in Redis:
- Tokens are cached with a TTL slightly less than expiration
- Expired tokens are automatically refreshed
- Cache can be manually invalidated if needed

```java
@Service
@RequiredArgsConstructor
public class TokenManagementService {

    private final CachedM2MAuthenticationService m2mAuthService;

    public void refreshToken() {
        // Invalidate cache to force token refresh
        m2mAuthService.invalidateCache();

        // Next call will fetch a fresh token
        String newToken = m2mAuthService.getAuthorizationHeader();
    }
}
```

### M2M with Specific Scopes

```java
public void callExternalService() {
    // Get token with specific scopes
    String authHeader = m2mAuthService.getAuthorizationHeaderWithScopes("read:users write:orders");

    // Use the token...
}
```

---

## Testing

### Using @WithMockJwt Annotation

```java
@SpringBootTest
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockJwt(subject = "auth0|test-user-123", scopes = {"read:orders"})
    public void testGetOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockJwt(
        subject = "auth0|admin-user",
        permissions = {"admin:access", "delete:orders"},
        admin = true
    )
    public void testDeleteOrder() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockJwt(
        subject = "auth0|user-123",
        email = "test@example.com",
        name = "Test User"
    )
    public void testGetProfile() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
            .andExpect(status().isOk());
    }
}
```

### Using JwtTestHelper

```java
@Test
public void testWithCustomJwt() {
    // Create custom JWT
    Jwt jwt = JwtTestHelper.createJwtWithScopes(
        "auth0|test-user",
        List.of("read:products", "write:products")
    );

    // Create authentication token
    JwtAuthenticationToken auth = JwtTestHelper.createAuthenticationToken(jwt);

    // Use in your test...
}
```

### Using SecurityContextTestHelper

```java
@Test
public void testWithSecurityContext() {
    // Setup security context for test
    SecurityContextTestHelper.setupSecurityContextWithScopes(
        "auth0|test-user",
        "read:orders",
        "write:orders"
    );

    // Call service method that uses SecurityContextUtil
    orderService.createOrder(orderRequest);

    // Clean up
    SecurityContextTestHelper.clearSecurityContext();
}

@Test
public void testAsAdmin() {
    // Setup admin security context
    SecurityContextTestHelper.setupAdminSecurityContext();

    // Test admin functionality
    adminService.deleteAllOrders();

    // Clean up
    SecurityContextTestHelper.clearSecurityContext();
}
```

---

## Best Practices

### 1. Always Validate User Ownership

```java
@Service
public class OrderService {

    public Order getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        String currentUserId = SecurityContextUtil.getCurrentUserId();

        // Check ownership unless admin
        if (!order.getUserId().equals(currentUserId) && !SecurityContextUtil.isAdmin()) {
            throw new ForbiddenException("You don't have access to this order");
        }

        return order;
    }
}
```

### 2. Use Method-Level Security

```java
@Service
@Validated
public class ProductService {

    @PreAuthorize("hasAnyAuthority('write:products', 'admin:access')")
    public Product updateProduct(Long id, ProductUpdateRequest request) {
        // Method is protected at service level
        return productRepository.save(product);
    }

    @PreAuthorize("hasAuthority('admin:access')")
    public void deleteProduct(Long id) {
        // Only admins can delete
        productRepository.deleteById(id);
    }
}
```

### 3. Handle Security Exceptions Gracefully

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ApiResponse(false, "Access denied: " + e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse> handleAuthenticationError(AuthenticationException e) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse(false, "Authentication failed: " + e.getMessage()));
    }
}
```

### 4. Use Constants for Permissions

```java
import static com.ecommerce.common.security.constants.SecurityConstants.*;

@Service
public class ProductService {

    @PreAuthorize("hasAuthority(T(com.ecommerce.common.security.constants.SecurityConstants).PERMISSION_WRITE_PRODUCTS)")
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
}
```

### 5. Log Security Events

```java
@Service
@Slf4j
public class AuditService {

    public void logSecurityEvent(String action, String resource) {
        String userId = SecurityContextUtil.getCurrentUserId();
        String email = SecurityContextUtil.getCurrentUserEmail();

        log.info("Security event: user={} email={} action={} resource={}",
            userId, email, action, resource);
    }
}
```

### 6. Test Authorization Logic

Always write tests for authorization:

```java
@SpringBootTest
public class OrderServiceSecurityTest {

    @Test
    @WithMockJwt(subject = "auth0|user-1")
    public void userCanAccessOwnOrders() {
        // Test that user can access their own orders
    }

    @Test
    @WithMockJwt(subject = "auth0|user-1")
    public void userCannotAccessOtherUserOrders() {
        // Test that user cannot access other user's orders
        assertThrows(ForbiddenException.class, () -> {
            orderService.getOrder(otherUserOrderId);
        });
    }

    @Test
    @WithMockJwt(admin = true)
    public void adminCanAccessAllOrders() {
        // Test that admin can access any order
    }
}
```

---

## Security Checklist

- [ ] Extended `BaseSecurityConfig` in your service
- [ ] Configured Auth0 properties (domain, audience)
- [ ] Configured M2M credentials for service-to-service communication
- [ ] Used appropriate authorization annotations (@IsAuthenticated, @IsAdmin, etc.)
- [ ] Validated resource ownership in service methods
- [ ] Used constants for permission/scope strings
- [ ] Implemented proper error handling for security exceptions
- [ ] Added security logging for audit trails
- [ ] Wrote unit tests for authorization logic
- [ ] Tested with mock JWT tokens using @WithMockJwt

---

## Common Issues and Solutions

### Issue: JWT validation fails

**Solution:** Check that your Auth0 domain and audience are correctly configured.

```yaml
auth0:
  domain: your-tenant.auth0.com  # No https:// prefix
  audience: https://api.ecommerce.com  # Must match Auth0 API identifier
```

### Issue: M2M token requests fail

**Solution:** Verify your M2M application credentials in Auth0 and ensure the application has the correct permissions.

### Issue: @WithMockJwt not working in tests

**Solution:** Make sure you have `@SpringBootTest` and Spring Security Test dependencies.

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Issue: Token caching not working

**Solution:** Verify Redis is running and properly configured.

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

## Additional Resources

- [Auth0 Documentation](https://auth0.com/docs)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [OAuth 2.0 Specification](https://oauth.net/2/)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)
