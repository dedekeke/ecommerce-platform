package com.ecommerce.gateway.aot;

import com.ecommerce.gateway.graphql.dto.CartDto;
import com.ecommerce.gateway.graphql.dto.CartItemDto;
import com.ecommerce.gateway.graphql.dto.CategoryDto;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.OrderItemDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import com.ecommerce.gateway.graphql.dto.PromotionDto;
import com.ecommerce.gateway.graphql.dto.RecommendationDto;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * §2.5 Reflection / serialization hints for GraalVM native image builds.
 *
 * <p>Spring AOT can statically discover most beans, but Jackson reflective
 * serialization and Spring Cloud Gateway route metadata need explicit hints
 * because the native image agent isn't run during a developer's normal
 * build. Rather than ship a full reachability-metadata bundle, we register
 * the BFF DTO surface here. Add classes when adding new GraphQL types.
 *
 * <p>Wired in {@code META-INF/spring/aot.factories} so it's invoked during
 * {@code mvn -Pnative native:compile}.
 */
public class GatewayRuntimeHints implements RuntimeHintsRegistrar {

    private static final Class<?>[] REFLECTIVE_DTOS = new Class<?>[] {
            CartDto.class,
            CartItemDto.class,
            CategoryDto.class,
            OrderDto.class,
            OrderItemDto.class,
            PageDto.class,
            ProductDto.class,
            PromotionDto.class,
            RecommendationDto.class
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (Class<?> dto : REFLECTIVE_DTOS) {
            hints.reflection().registerType(dto,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
            hints.serialization().registerType(TypeReference.of(dto));
        }
        // GraphQL schema files are loaded reflectively from the classpath.
        hints.resources().registerPattern("graphql/*.graphqls");
        hints.resources().registerPattern("graphql/**/*.graphqls");
    }
}
