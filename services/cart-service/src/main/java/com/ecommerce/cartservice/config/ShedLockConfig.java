package com.ecommerce.cartservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock wiring so the abandoned-cart scan and the expired/abandoned cleanup
 * jobs fire on exactly one instance at a time when cart-service is scaled out.
 * Without it, every replica would publish duplicate cart.abandoned reminders
 * and race on the same cleanup rows.
 *
 * <p>JDBC-template provider against the service's PostgreSQL datasource; the
 * {@code shedlock} table ships as a Flyway migration. {@code usingDbTime()}
 * anchors the lock clock to the database to avoid node clock skew.
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
