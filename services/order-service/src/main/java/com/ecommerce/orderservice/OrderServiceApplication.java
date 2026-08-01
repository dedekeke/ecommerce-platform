package com.ecommerce.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

// JPA repositories, Kafka and scheduling are enabled in PersistenceConfig so
// that @WebMvcTest slices don't eagerly bootstrap the persistence layer.
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
