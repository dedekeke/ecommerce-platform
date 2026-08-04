package com.ecommerce.orderservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link ShedLockConfig}. Constructing the provider must not touch
 * the database (it is used lazily at lock time), so a mock DataSource suffices —
 * this keeps the assertion runnable without Docker.
 */
class ShedLockConfigTest {

    @Test
    void should_provideJdbcTemplateLockProvider_when_dataSourceGiven() {
        DataSource dataSource = mock(DataSource.class);

        LockProvider provider = new ShedLockConfig().lockProvider(dataSource);

        assertThat(provider).isInstanceOf(JdbcTemplateLockProvider.class);
    }
}
