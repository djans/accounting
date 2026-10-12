package com.cogitosum.config;

import com.cogitosum.service.UserShortIdService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class LoggingWebConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<UserActivityLoggingInterceptor> interceptorProvider;

    public LoggingWebConfiguration(ObjectProvider<UserActivityLoggingInterceptor> interceptorProvider) {
        this.interceptorProvider = interceptorProvider;
    }

    @Bean
    @ConditionalOnBean(UserShortIdService.class)
    UserActivityLoggingInterceptor userActivityLoggingInterceptor(UserShortIdService shortIdService) {
        return new UserActivityLoggingInterceptor(shortIdService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        UserActivityLoggingInterceptor interceptor = interceptorProvider.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor)
                    .excludePathPatterns("/actuator/**", "/css/**", "/js/**", "/vendor/**", "/favicon.ico");
        }
    }
}
