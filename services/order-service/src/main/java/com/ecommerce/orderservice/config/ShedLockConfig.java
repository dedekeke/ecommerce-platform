package com.ecommerce.orderservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock wiring so non-idempotent {@code @Scheduled} jobs fire on exactly one
 * instance at a time once order-service is scaled to more than one replica.
 *
 * <p>Uses the JDBC-template provider against the service's own PostgreSQL
 * datasource — no extra infrastructure (the {@code shedlock} table ships as a
 * Flyway migration). {@code usingDbTime()} makes the lock's clock the database
 * clock, which sidesteps inter-node clock skew.
 *
 * <p>{@code defaultLockAtMostFor} is a safety net: if an instance dies holding
 * a lock, the lock is force-released after this duration so work is not stalled
 * forever. Individual jobs tighten {@code lockAtMostFor}/{@code lockAtLeastFor}
 * to match their cadence.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
