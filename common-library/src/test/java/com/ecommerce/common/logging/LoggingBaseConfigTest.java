package com.ecommerce.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.Status;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the shared logging base ({@code logback-includes/logging-base.xml}) parses and routes
 * appenders correctly per Spring profile.
 *
 * <p>The base is included from {@code logback-test-wrapper.xml} (mirroring a real service's
 * {@code logback-spring.xml}) and applied through Spring Boot's public {@link LoggingSystem} API,
 * the same path used at application startup. This honours {@code <springProfile>} blocks, so the
 * test exercises the real wiring: human-readable CONSOLE for local/default, structured JSON
 * (LogstashEncoder) for {@code prod}/{@code json-logging}.
 */
@DisplayName("logging-base.xml")
class LoggingBaseConfigTest {

    private static final String WRAPPER = "classpath:logback-test-wrapper.xml";

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void tearDown() {
        loggingSystem.cleanUp();
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
    }

    @Test
    @DisplayName("should_routeRootToJsonLogstashEncoder_when_prodProfileActive")
    void should_routeRootToJsonLogstashEncoder_when_prodProfileActive() {
        configure("prod");

        ConsoleAppender<?> json = (ConsoleAppender<?>) rootAppender("JSON");

        assertThat(json.getEncoder()).isInstanceOf(LogstashEncoder.class);
    }

    @Test
    @DisplayName("should_routeRootToJsonLogstashEncoder_when_jsonLoggingProfileActive")
    void should_routeRootToJsonLogstashEncoder_when_jsonLoggingProfileActive() {
        configure("json-logging");

        ConsoleAppender<?> json = (ConsoleAppender<?>) rootAppender("JSON");

        assertThat(json.getEncoder()).isInstanceOf(LogstashEncoder.class);
    }

    @Test
    @DisplayName("should_routeRootToJsonLogstashEncoder_when_dockerProfileActive")
    void should_routeRootToJsonLogstashEncoder_when_dockerProfileActive() {
        // docker-compose runs every service with SPRING_PROFILES_ACTIVE=docker; it must emit JSON.
        configure("docker");

        ConsoleAppender<?> json = (ConsoleAppender<?>) rootAppender("JSON");

        assertThat(json.getEncoder()).isInstanceOf(LogstashEncoder.class);
    }

    @Test
    @DisplayName("should_notRouteRootToConsole_when_dockerProfileActive")
    void should_notRouteRootToConsole_when_dockerProfileActive() {
        configure("docker");

        assertThat(loggerContext().getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE")).isNull();
    }

    @Test
    @DisplayName("should_emitServiceFieldFromAppName_when_jsonEncoderConfigured")
    void should_emitServiceFieldFromAppName_when_jsonEncoderConfigured() {
        configure("prod");

        ConsoleAppender<?> json = (ConsoleAppender<?>) rootAppender("JSON");
        LogstashEncoder encoder = (LogstashEncoder) json.getEncoder();

        assertThat(encoder.getCustomFields()).contains("\"service\":\"test-service\"");
    }

    @Test
    @DisplayName("should_routeRootToHumanReadableConsole_when_noJsonProfileActive")
    void should_routeRootToHumanReadableConsole_when_noJsonProfileActive() {
        configure();

        ConsoleAppender<?> console = (ConsoleAppender<?>) rootAppender("CONSOLE");
        Encoder<?> encoder = console.getEncoder();

        assertThat(encoder).isInstanceOf(PatternLayoutEncoder.class);
        assertThat(((PatternLayoutEncoder) encoder).getPattern()).contains("traceId=%X{traceId");
    }

    @Test
    @DisplayName("should_notRouteRootToJson_when_localProfileActive")
    void should_notRouteRootToJson_when_localProfileActive() {
        configure("local");

        assertThat(loggerContext().getLogger(Logger.ROOT_LOGGER_NAME).getAppender("JSON")).isNull();
    }

    @Test
    @DisplayName("should_parseWithoutErrors_when_loaded")
    void should_parseWithoutErrors_when_loaded() {
        configure("prod");

        assertThat(loggerContext().getStatusManager().getCopyOfStatusList())
                .noneMatch(status -> status.getLevel() == Status.ERROR);
    }

    private void configure(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "test-service");
        environment.setActiveProfiles(activeProfiles);

        LoggingInitializationContext initContext = new LoggingInitializationContext(environment);
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(initContext, WRAPPER, null);
    }

    private LoggerContext loggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    private Appender<?> rootAppender(String name) {
        Appender<?> appender = loggerContext().getLogger(Logger.ROOT_LOGGER_NAME).getAppender(name);
        assertThat(appender).as("root appender '%s'", name).isNotNull();
        return appender;
    }
}
