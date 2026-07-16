package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.BaseMongoTest;
import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for NotificationTemplateRepository
 * Following TDD principles
 */
class NotificationTemplateRepositoryTest extends BaseMongoTest {

    @Autowired
    private NotificationTemplateRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveNotificationTemplate() {
        // Given
        Map<String, Object> defaultVars = new HashMap<>();
        defaultVars.put("companyName", "E-Commerce Platform");

        NotificationTemplate template = NotificationTemplate.builder()
                .code("ORDER_CONFIRMATION")
                .name("Order Confirmation")
                .description("Email sent when order is confirmed")
                .type(NotificationType.EMAIL)
                .subject("Your Order #${orderNumber} has been confirmed")
                .body("order-confirmation")
                .defaultVariables(defaultVars)
                .active(true)
                .build();

        // When
        NotificationTemplate saved = repository.save(template);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("ORDER_CONFIRMATION");
        assertThat(saved.getType()).isEqualTo(NotificationType.EMAIL);
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    void shouldFindByCode() {
        // Given
        NotificationTemplate template = createTemplate("ORDER_CONFIRMATION", "Order Confirmation");
        repository.save(template);

        // When
        Optional<NotificationTemplate> result = repository.findByCode("ORDER_CONFIRMATION");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("ORDER_CONFIRMATION");
    }

    @Test
    void shouldReturnEmptyWhenCodeNotFound() {
        // When
        Optional<NotificationTemplate> result = repository.findByCode("NON_EXISTENT");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByTypeAndActive() {
        // Given
        NotificationTemplate activeEmail1 = createTemplate("EMAIL_1", "Email 1");
        activeEmail1.setType(NotificationType.EMAIL);
        activeEmail1.setActive(true);

        NotificationTemplate activeEmail2 = createTemplate("EMAIL_2", "Email 2");
        activeEmail2.setType(NotificationType.EMAIL);
        activeEmail2.setActive(true);

        NotificationTemplate inactiveEmail = createTemplate("EMAIL_3", "Email 3");
        inactiveEmail.setType(NotificationType.EMAIL);
        inactiveEmail.setActive(false);

        NotificationTemplate activeSms = createTemplate("SMS_1", "SMS 1");
        activeSms.setType(NotificationType.SMS);
        activeSms.setActive(true);

        repository.saveAll(List.of(activeEmail1, activeEmail2, inactiveEmail, activeSms));

        // When
        List<NotificationTemplate> result = repository.findByTypeAndActive(NotificationType.EMAIL, true);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result)
                .allMatch(t -> t.getType() == NotificationType.EMAIL && t.getActive());
    }

    @Test
    void shouldFindByActive() {
        // Given
        NotificationTemplate active1 = createTemplate("ACTIVE_1", "Active 1");
        active1.setActive(true);

        NotificationTemplate active2 = createTemplate("ACTIVE_2", "Active 2");
        active2.setActive(true);

        NotificationTemplate inactive = createTemplate("INACTIVE_1", "Inactive 1");
        inactive.setActive(false);

        repository.saveAll(List.of(active1, active2, inactive));

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<NotificationTemplate> result = repository.findByActive(true, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(NotificationTemplate::getActive);
    }

    @Test
    void shouldUpdateTemplate() {
        // Given
        NotificationTemplate template = createTemplate("TEST_TEMPLATE", "Test Template");
        template.setActive(true);
        NotificationTemplate saved = repository.save(template);

        // When
        saved.setActive(false);
        saved.setSubject("Updated Subject");
        NotificationTemplate updated = repository.save(saved);

        // Then
        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getSubject()).isEqualTo("Updated Subject");
    }

    @Test
    void shouldDeleteTemplate() {
        // Given
        NotificationTemplate template = createTemplate("DELETE_ME", "Delete Me");
        NotificationTemplate saved = repository.save(template);

        // When
        repository.deleteById(saved.getId());

        // Then
        Optional<NotificationTemplate> result = repository.findById(saved.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindAllTemplates() {
        // Given
        repository.saveAll(List.of(
                createTemplate("TEMPLATE_1", "Template 1"),
                createTemplate("TEMPLATE_2", "Template 2"),
                createTemplate("TEMPLATE_3", "Template 3")
        ));

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<NotificationTemplate> result = repository.findAll(pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void should_rejectDuplicateCode_when_uniqueIndexEnforced() {
        // Given a seeded template code.
        repository.insert(createTemplate("UNIQUE_CODE", "First"));

        // When a second insert reuses the same code / Then the unique index rejects it.
        NotificationTemplate duplicate = createTemplate("UNIQUE_CODE", "Second");
        assertThatThrownBy(() -> repository.insert(duplicate))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private NotificationTemplate createTemplate(String code, String name) {
        return NotificationTemplate.builder()
                .code(code)
                .name(name)
                .description("Test template")
                .type(NotificationType.EMAIL)
                .subject("Test Subject")
                .body("test-template")
                .active(true)
                .build();
    }
}
