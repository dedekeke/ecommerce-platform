package com.ecommerce.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;

/**
 * Email Service
 * Handles sending emails with Thymeleaf templates
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.email.from:noreply@ecommerce.com}")
    private String fromEmail;

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Send email with HTML template
     */
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            if (!emailEnabled) {
                log.info("[MOCK] Email would be sent to: {}, subject: {}, template: {}", to, subject, templateName);
                log.debug("[MOCK] Email variables: {}", variables);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            // Process Thymeleaf template
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        } catch (RuntimeException e) {
            // Wrap unchecked failures (template engine errors, mail sender issues)
            // so callers see a consistent "Failed to send email" message and can
            // distinguish notification failures from generic runtime errors.
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send plain text email
     */
    public void sendPlainTextEmail(String to, String subject, String body) {
        try {
            if (!emailEnabled) {
                log.info("[MOCK] Plain text email would be sent to: {}, subject: {}", to, subject);
                log.debug("[MOCK] Email body: {}", body);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);
            log.info("Plain text email sent successfully to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send plain text email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        } catch (RuntimeException e) {
            log.error("Failed to send plain text email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
