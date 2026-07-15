package com.ecommerce.notificationservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that application.yml wires bounded SMTP timeouts so a stalled mail peer
 * cannot hang the sender thread (and, via the retry scheduler, the whole
 * {@code @Scheduled} pipeline). Env-driven with a 5000ms default.
 */
class MailTimeoutConfigTest {

    private static final String CONNECTION_TIMEOUT =
            "spring.mail.properties.mail.smtp.connectiontimeout";
    private static final String READ_TIMEOUT =
            "spring.mail.properties.mail.smtp.timeout";
    private static final String WRITE_TIMEOUT =
            "spring.mail.properties.mail.smtp.writetimeout";

    private Properties properties;
    private StandardEnvironment environment;

    @BeforeEach
    void loadApplicationYaml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        properties = yaml.getObject();

        environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new PropertiesPropertySource("application", properties));
    }

    @Test
    void should_declare_all_three_smtp_timeout_properties() {
        assertThat(properties).containsKeys(CONNECTION_TIMEOUT, READ_TIMEOUT, WRITE_TIMEOUT);
    }

    @Test
    void should_bind_env_driven_placeholders_for_smtp_timeouts() {
        assertThat(properties.getProperty(CONNECTION_TIMEOUT))
                .isEqualTo("${MAIL_SMTP_CONNECTION_TIMEOUT:5000}");
        assertThat(properties.getProperty(READ_TIMEOUT))
                .isEqualTo("${MAIL_SMTP_TIMEOUT:5000}");
        assertThat(properties.getProperty(WRITE_TIMEOUT))
                .isEqualTo("${MAIL_SMTP_WRITE_TIMEOUT:5000}");
    }

    @Test
    void should_resolve_to_sane_5000ms_defaults_when_env_absent() {
        assertThat(environment.resolvePlaceholders(properties.getProperty(CONNECTION_TIMEOUT)))
                .isEqualTo("5000");
        assertThat(environment.resolvePlaceholders(properties.getProperty(READ_TIMEOUT)))
                .isEqualTo("5000");
        assertThat(environment.resolvePlaceholders(properties.getProperty(WRITE_TIMEOUT)))
                .isEqualTo("5000");
    }
}
