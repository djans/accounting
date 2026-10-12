package com.cogitosum.config;

import com.cogitosum.service.AppUserDetailsService;
import com.cogitosum.service.CurrentCompanyContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppUserDetailsService appUserDetailsService;
    private final DatabaseSchemaLoginSuccessHandler loginSuccessHandler;

    public SecurityConfig(
            AppUserDetailsService appUserDetailsService,
            DatabaseSchemaLoginSuccessHandler loginSuccessHandler) {
        this.appUserDetailsService = appUserDetailsService;
        this.loginSuccessHandler = loginSuccessHandler;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public CompanyRoleAuthorizationFilter companyRoleAuthorizationFilter(
            CurrentCompanyContext companyContext) {
        return new CompanyRoleAuthorizationFilter(companyContext);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<CompanyRoleAuthorizationFilter> companyRoleFilterRegistration(
            CompanyRoleAuthorizationFilter companyRoleAuthorizationFilter) {
        FilterRegistrationBean<CompanyRoleAuthorizationFilter> registration =
            new FilterRegistrationBean<>(companyRoleAuthorizationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CompanyRoleAuthorizationFilter companyRoleAuthorizationFilter) throws Exception {
        http
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .addFilterBefore(companyRoleAuthorizationFilter, AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                .requestMatchers("/login", "/favicon.ico", "/css/**", "/js/**", "/vendor/**").permitAll()
                .requestMatchers("/database/schema").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/database/schema/apply").hasRole("SUPER_ADMIN")
                .requestMatchers("/account/credentials/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/database/backup").hasRole("ADMIN")
                .requestMatchers("/database/backup/export", "/database/backup/import").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/reset", "/admin/reset/**").hasRole("ADMIN")
                .requestMatchers("/admin/monitoring", "/actuator", "/actuator/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/database/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/invoice-templates/**").hasRole("ADMIN")
                .requestMatchers("/database/query", "/database/query/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/database/migration/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/companies").authenticated()
                .requestMatchers(HttpMethod.POST, "/companies/*/select").authenticated()
                .requestMatchers(HttpMethod.GET, "/companies/new").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/companies").hasRole("SUPER_ADMIN")
                .requestMatchers("/companies/**", "/users/**", "/api/companies/**", "/api/users/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST,
                    "/payments/**", "/bills/*/pay", "/cheques/*/issue", "/cheques/*/void",
                    "/tax/periods/*/pay")
                    .hasAnyRole("ADMIN", "ACCOUNTANT", "BOOKKEEPER", "PAYMENT_OPERATOR")
                .requestMatchers("/reconcile/**").hasAnyRole("ADMIN", "ACCOUNTANT", "BOOKKEEPER")
                .requestMatchers(HttpMethod.POST, "/**").hasAnyRole("ADMIN", "ACCOUNTANT", "BOOKKEEPER")
                .requestMatchers(HttpMethod.PUT, "/**").hasAnyRole("ADMIN", "ACCOUNTANT", "BOOKKEEPER")
                .requestMatchers(HttpMethod.PATCH, "/**").hasAnyRole("ADMIN", "ACCOUNTANT", "BOOKKEEPER")
                .requestMatchers(HttpMethod.DELETE, "/**").hasAnyRole("ADMIN", "ACCOUNTANT", "BOOKKEEPER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(loginSuccessHandler)
                .failureUrl("/login?error"))
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
