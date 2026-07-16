package com.ecommerce.notificationservice.config;

import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the insert-first seeding strategy: seeding must not rely on the
 * non-atomic existsByCode check, and a concurrent double-insert (surfaced as a
 * DuplicateKeyException by the unique index on code) must be tolerated.
 */
@ExtendWith(MockitoExtension.class)
class NotificationTemplateInitializerTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private NotificationTemplateInitializer initializer;

    @Test
    void should_insertViaInsertFirst_andNeverCheckThenInsert() {
        when(templateRepository.insert(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run();

        // Insert-first: templates are inserted, never gated on a prior existsByCode.
        verify(templateRepository, atLeastOnce()).insert(any(NotificationTemplate.class));
        verify(templateRepository, never()).existsByCode(anyString());
        verify(templateRepository, never()).save(any(NotificationTemplate.class));
    }

    @Test
    void should_notPropagate_when_concurrentInstanceAlreadySeeded() {
        // The unique index rejects the racing duplicate insert; startup must survive it.
        when(templateRepository.insert(any(NotificationTemplate.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key on code"));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verify(templateRepository, atLeastOnce()).insert(any(NotificationTemplate.class));
    }
}
