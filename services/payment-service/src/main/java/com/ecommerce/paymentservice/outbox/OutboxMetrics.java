package com.ecommerce.paymentservice.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;

@Configuration
@ConditionalOnBean(MeterRegistry.class)
public class OutboxMetrics {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private OutboxRepository outboxRepository;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("outbox.unpublished.count", outboxRepository,
                repo -> (double) repo.countByPublishedAtIsNull())
            .description("Number of outbox events awaiting publication to Kafka")
            .tag("service", "payment-service")
            .register(meterRegistry);
    }
}
