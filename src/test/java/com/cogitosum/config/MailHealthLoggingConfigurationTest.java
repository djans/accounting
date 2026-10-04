package com.cogitosum.config;

import jakarta.mail.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class MailHealthLoggingConfigurationTest {

    @Test
    void logsSmtpFailureWithoutStackTrace(CapturedOutput output) throws Exception {
        JavaMailSenderImpl mailSender = mock(JavaMailSenderImpl.class);
        when(mailSender.getHost()).thenReturn("smtp.example.com");
        when(mailSender.getPort()).thenReturn(587);
        doThrow(new AuthenticationFailedException("failed to connect, no password specified?"))
                .when(mailSender).testConnection();

        new ApplicationContextRunner()
                .withUserConfiguration(MailHealthLoggingConfiguration.class,
                        MailHealthContributorAutoConfiguration.class)
                .withBean(JavaMailSenderImpl.class, () -> mailSender)
                .withPropertyValues("spring.mail.host=smtp.example.com",
                        "spring.mail.username=mailer@example.com",
                        "management.health.mail.enabled=false")
                .run(context -> {
                    HealthIndicator indicator = context.getBean("mailHealthIndicator", HealthIndicator.class);
                    Health health = indicator.health();
                    assertEquals(Status.DOWN, health.getStatus());
                    assertEquals("SMTP authentication failed: password is not configured",
                            health.getDetails().get("error"));
                    assertFalse(context.containsBean("mailHealthContributor"));
                });

        assertFalse(output.getOut().contains("username=mailer@example.com"));
        assertTrue(output.getOut().contains("SMTP authentication failed: password is not configured"));
        assertFalse(output.getOut().contains("failed to connect, no password specified?"));
        assertFalse(output.getOut().contains("\tat "));
    }
}
