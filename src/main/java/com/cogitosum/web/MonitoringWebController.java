package com.cogitosum.web;

import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/monitoring")
public class MonitoringWebController {

    private static final int MAX_METRICS = 40;

    private final HealthEndpoint healthEndpoint;
    private final MetricsEndpoint metricsEndpoint;

    public MonitoringWebController(HealthEndpoint healthEndpoint, MetricsEndpoint metricsEndpoint) {
        this.healthEndpoint = healthEndpoint;
        this.metricsEndpoint = metricsEndpoint;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        requireSuperAdmin(authentication);

        HealthDescriptor health = healthEndpoint.health();
        List<HealthComponent> components = new ArrayList<>();
        components.add(new HealthComponent("application", health.getStatus().getCode()));
        if (health instanceof CompositeHealthDescriptor composite) {
            composite.getComponents().forEach((name, component) ->
                    collectHealthComponents(name, component, components));
        }

        List<MetricValue> metrics = metricsEndpoint.listNames().getNames().stream()
                .sorted()
                .limit(MAX_METRICS)
                .map(name -> metricsEndpoint.metric(name, List.of()))
                .filter(metric -> metric != null)
                .flatMap(metric -> metric.getMeasurements().stream()
                        .map(sample -> {
                            FormattedMetricValue formatted =
                                    formatMetricValue(sample.getValue(), metric.getBaseUnit());
                            return new MetricValue(
                                    metric.getName(),
                                    sample.getStatistic().name().toLowerCase(Locale.ROOT),
                                    formatted.value(),
                                    formatted.unit());
                        }))
                .toList();

        model.addAttribute("active", "admin-monitoring");
        model.addAttribute("healthStatus", health.getStatus().getCode());
        model.addAttribute("healthComponents", components);
        model.addAttribute("metrics", metrics);
        model.addAttribute("actuatorHealthUrl", "/actuator/health");
        model.addAttribute("actuatorMetricsUrl", "/actuator/metrics");
        return "admin/monitoring";
    }

    private void collectHealthComponents(String parent, HealthDescriptor descriptor,
                                        List<HealthComponent> components) {
        if (descriptor instanceof CompositeHealthDescriptor composite
                && !composite.getComponents().isEmpty()) {
            composite.getComponents().forEach((name, component) ->
                    collectHealthComponents(parent.isEmpty() ? name : parent + "." + name,
                            component, components));
            return;
        }
        components.add(new HealthComponent(parent, descriptor.getStatus().getCode()));
    }

    private void requireSuperAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super administrator role required");
        }
    }

    static FormattedMetricValue formatMetricValue(double value, String baseUnit) {
        if (baseUnit == null || !(baseUnit.equalsIgnoreCase("byte") || baseUnit.equalsIgnoreCase("bytes"))) {
            return new FormattedMetricValue(Double.toString(value), baseUnit == null ? "" : baseUnit);
        }
        if (!Double.isFinite(value)) {
            return new FormattedMetricValue(Double.toString(value), "B");
        }

        String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB"};
        double scaledValue = value;
        int unitIndex = 0;
        while (Math.abs(scaledValue) >= 1024 && unitIndex < units.length - 1) {
            scaledValue /= 1024;
            unitIndex++;
        }
        String displayValue = BigDecimal.valueOf(scaledValue)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return new FormattedMetricValue(displayValue, units[unitIndex]);
    }

    public record HealthComponent(String name, String status) {
    }

    public record MetricValue(String name, String statistic, String value, String unit) {
    }

    record FormattedMetricValue(String value, String unit) {
    }
}
