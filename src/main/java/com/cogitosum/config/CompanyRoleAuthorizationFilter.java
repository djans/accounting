package com.cogitosum.config;

import com.cogitosum.service.CurrentCompanyContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompanyRoleAuthorizationFilter extends OncePerRequestFilter {

    private final CurrentCompanyContext companyContext;

    public CompanyRoleAuthorizationFilter(CurrentCompanyContext companyContext) {
        this.companyContext = companyContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            companyContext.currentMembershipRole()
                .ifPresent(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
            if (companyContext.isSuperAdmin()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            }
            UsernamePasswordAuthenticationToken companyAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(authentication.getPrincipal(), null, authorities);
            companyAuthentication.setDetails(authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details : authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(companyAuthentication);
        }
        filterChain.doFilter(request, response);
    }
}
