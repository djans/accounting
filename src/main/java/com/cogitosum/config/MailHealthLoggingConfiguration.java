package com.cogitosum.config;

import jakarta.mail.MessagingException;
import jakarta.mail.AuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class MailHealthLoggingConfiguration {

    @Bean(name = "mailHealthIndicator")
    @Conditional(SmtpHostConfiguredCondition.class)
    HealthIndicator mailHealthIndicator(JavaMailSenderImpl mailSender) {
        return new LoggingMailHealthIndicator(mailSender);
    }

    public static class SmtpHostConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("spring.mail.host"));
        }
    }

    private static final class LoggingMailHealthIndicator implements HealthIndicator {
        private static final Logger logger = LoggerFactory.getLogger(LoggingMailHealthIndicator.class);

        private final JavaMailSenderImpl mailSender;

        private LoggingMailHealthIndicator(JavaMailSenderImpl mailSender) {
            this.mailSender = mailSender;
        }

        @Override
        public Health health() {
            try {
                mailSender.testConnection();
                return Health.up()
                        .withDetail("location", mailSender.getHost() + ":" + mailSender.getPort())
                        .build();
            } catch (MessagingException | MailException exception) {
                String error = describe(exception);
                logger.warn("Mail health check failed (host={}, port={}): {}",
                        mailSender.getHost(), mailSender.getPort(), error);
                return Health.down().withDetail("error", error).build();
            }
        }

        private static String describe(Throwable failure) {
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Throwable root = failure;
            while (root.getCause() != null && seen.add(root)) {
                root = root.getCause();
            }
            if (root instanceof AuthenticationFailedException
                    && root.getMessage() != null
                    && root.getMessage().toLowerCase().contains("no password specified")) {
                return "SMTP authentication failed: password is not configured";
            }
            Throwable detail = StringUtils.hasText(root.getMessage()) ? root : failure;
            String message = detail.getMessage();
            return detail.getClass().getSimpleName()
                    + (StringUtils.hasText(message) ? ": " + message : "");
        }
    }
}
