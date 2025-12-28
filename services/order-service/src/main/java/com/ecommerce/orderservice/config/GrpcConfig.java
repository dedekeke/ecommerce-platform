package com.ecommerce.orderservice.config;

import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client configuration with connection pooling and timeouts
 */
@Configuration
public class GrpcConfig {

    /**
     * Configure gRPC channels with optimized settings:
     * - Connection pooling via channel sharing
     * - Keep-alive settings for connection health
     * - Timeouts for operations
     * - Retry policies
     */
    @Bean
    public GrpcChannelConfigurer grpcChannelConfigurer() {
        return (channelBuilder, name) -> {
            if (channelBuilder instanceof ManagedChannelBuilder<?> managedChannelBuilder) {
                managedChannelBuilder
                    // Keep-alive settings
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)

                    // Idle timeout - close channel after 5 minutes of inactivity
                    .idleTimeout(5, TimeUnit.MINUTES)

                    // Max inbound message size (10 MB)
                    .maxInboundMessageSize(10 * 1024 * 1024)

                    // Enable retry
                    .enableRetry()
                    .maxRetryAttempts(3);
            }
        };
    }
}
