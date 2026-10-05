package com.cogitosum.config;

import com.cogitosum.service.AppUserDetailsService;
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
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                .requestMatchers("/login", "/favicon.ico", "/css/**", "/js/**", "/vendor/**").permitAll()
                .requestMatchers("/database/schema").authenticated()
                .requestMatchers(HttpMethod.POST, "/database/schema/apply").hasRole("ADMIN")
                .requestMatchers("/account/credentials/**").authenticated()
                .requestMatchers("/database/backup/**").hasRole("ADMIN")
                .requestMatchers("/admin/reset", "/admin/reset/**").hasRole("ADMIN")
                .requestMatchers("/admin/database/**").hasRole("ADMIN")
                .requestMatchers("/database/query", "/database/query/**").hasRole("ADMIN")
                .requestMatchers("/database/migration/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/companies").authenticated()
                .requestMatchers(HttpMethod.POST, "/companies/*/select").authenticated()
                .requestMatchers("/companies/**", "/users/**", "/api/companies/**", "/api/users/**").hasRole("ADMIN")
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
