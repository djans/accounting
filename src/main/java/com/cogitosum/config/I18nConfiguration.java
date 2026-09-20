package com.cogitosum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.context.annotation.Primary;

import java.util.Locale;

@Configuration
public class I18nConfiguration implements WebMvcConfigurer {

    @Bean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang") {
            @Override
            public Locale resolveLocale(jakarta.servlet.http.HttpServletRequest request) {
                return languageOnly(super.resolveLocale(request));
            }

            @Override
            public LocaleContext resolveLocaleContext(jakarta.servlet.http.HttpServletRequest request) {
                return new SimpleLocaleContext(languageOnly(super.resolveLocale(request)));
            }

            private Locale languageOnly(Locale locale) {
                return locale != null && "fr".equalsIgnoreCase(locale.getLanguage())
                        ? Locale.CANADA_FRENCH
                        : Locale.CANADA;
            }
        };
        resolver.setDefaultLocale(Locale.CANADA);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Bean(name = "messageSource")
    @Primary
    public ReloadableResourceBundleMessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        // Common/shared bundle plus one per-feature bundle under i18n/. Keys are
        // unique across bundles (prefixed per feature), so basename order is not significant.
        messageSource.setBasenames(
                "classpath:messages",
                "classpath:i18n/contacts",
                "classpath:i18n/billing",
                "classpath:i18n/treasury",
                "classpath:i18n/gl1",
                "classpath:i18n/gl2",
                "classpath:i18n/reports",
                "classpath:i18n/taxa",
                "classpath:i18n/taxb"
        );
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(false);
        // Do not fall back to the JVM/system locale (e.g. fr_CA) for missing keys;
        // resolve a region locale like fr_CA against messages_fr, then stop.
        messageSource.setFallbackToSystemLocale(false);
        // Short cache so message edits are picked up quickly in development.
        messageSource.setCacheSeconds(5);
        return messageSource;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
