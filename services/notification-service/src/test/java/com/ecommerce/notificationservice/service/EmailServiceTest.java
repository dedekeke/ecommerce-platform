package com.ecommerce.notificationservice.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class for EmailService
 * Following TDD principles
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage((Session) null);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@ecommerce.com");
        ReflectionTestUtils.setField(emailService, "emailEnabled", false); // Mock mode by default
    }

    @Test
    void shouldSendEmailInMockMode() {
        // Given
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", "John Doe");
        variables.put("orderNumber", "ORD-12345");

        // When
        emailService.sendEmail(
                "test@example.com",
                "Order Confirmation",
                "order-confirmation",
                variables
        );

        // Then
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(templateEngine, never()).process(anyString(), any(Context.class));
    }

    @Test
    void shouldSendEmailWhenEnabled() {
        // Given
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", "John Doe");
        variables.put("orderNumber", "ORD-12345");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("order-confirmation"), any(Context.class)))
                .thenReturn("<html><body>Email content</body></html>");

        // When
        emailService.sendEmail(
                "test@example.com",
                "Order Confirmation",
                "order-confirmation",
                variables
        );

        // Then
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("order-confirmation"), contextCaptor.capture());

        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("userName")).isEqualTo("John Doe");
        assertThat(capturedContext.getVariable("orderNumber")).isEqualTo("ORD-12345");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldProcessThymeleafTemplate() {
        // Given
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", "John Doe");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html><body>Hello John Doe</body></html>");

        // When
        emailService.sendEmail(
                "test@example.com",
                "Test Email",
                "test-template",
                variables
        );

        // Then
        verify(templateEngine).process(eq("test-template"), any(Context.class));
    }

    @Test
    void shouldSendPlainTextEmailInMockMode() {
        // When
        emailService.sendPlainTextEmail(
                "test@example.com",
                "Test Subject",
                "Plain text body"
        );

        // Then
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendPlainTextEmailWhenEnabled() {
        // Given
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        emailService.sendPlainTextEmail(
                "test@example.com",
                "Test Subject",
                "Plain text body"
        );

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldThrowExceptionOnEmailFailure() {
        // Given
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);

        Map<String, Object> variables = new HashMap<>();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // When/Then
        assertThatThrownBy(() -> emailService.sendEmail(
                "test@example.com",
                "Test",
                "template",
                variables
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to send email");
    }
}
