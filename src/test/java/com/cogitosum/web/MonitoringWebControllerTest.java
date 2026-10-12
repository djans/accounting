package com.cogitosum.web;

import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MonitoringWebController.class)
@AutoConfigureMockMvc(addFilters = false)
class MonitoringWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthEndpoint healthEndpoint;

    @MockitoBean
    private MetricsEndpoint metricsEndpoint;

    @Test
    void rendersHealthStatusesAndMetricsWithoutHealthDetails() throws Exception {
        CompositeHealthDescriptor health = mock(CompositeHealthDescriptor.class);
        when(health.getStatus()).thenReturn(org.springframework.boot.health.contributor.Status.UP);
        when(health.getComponents()).thenReturn(Map.of());
        when(healthEndpoint.health()).thenReturn(health);
        MetricsEndpoint.MetricNamesDescriptor names = mock(MetricsEndpoint.MetricNamesDescriptor.class);
        when(names.getNames()).thenReturn(Set.of("jvm.memory.used"));
        when(metricsEndpoint.listNames()).thenReturn(names);
        MetricsEndpoint.MetricDescriptor memoryMetric = mock(MetricsEndpoint.MetricDescriptor.class);
        MetricsEndpoint.Sample sample = mock(MetricsEndpoint.Sample.class);
        when(metricsEndpoint.metric("jvm.memory.used", List.of())).thenReturn(memoryMetric);
        when(memoryMetric.getName()).thenReturn("jvm.memory.used");
        when(memoryMetric.getBaseUnit()).thenReturn("bytes");
        when(memoryMetric.getMeasurements()).thenReturn(List.of(sample));
        when(sample.getStatistic()).thenReturn(Statistic.VALUE);
        when(sample.getValue()).thenReturn(1536.0);

        mockMvc.perform(get("/admin/monitoring").principal(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/monitoring"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("application")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UP")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Filter metrics by name")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-table-filter-text=\"jvm.memory.used\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KiB")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/actuator/health")));
    }

    @Test
    void formatsByteMetricsUsingReadableBinaryUnits() {
        assertEquals(new MonitoringWebController.FormattedMetricValue("1.5", "KiB"),
                MonitoringWebController.formatMetricValue(1536, "bytes"));
        assertEquals(new MonitoringWebController.FormattedMetricValue("2", "MiB"),
                MonitoringWebController.formatMetricValue(2_097_152, "bytes"));
        assertEquals(new MonitoringWebController.FormattedMetricValue("12.0", "requests"),
                MonitoringWebController.formatMetricValue(12, "requests"));
    }

    @Test
    void rejectsNonAdministrators() {
        Authentication user = mock(Authentication.class);
        when(user.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))).when(user).getAuthorities();

        MonitoringWebController controller = new MonitoringWebController(healthEndpoint, metricsEndpoint);
        assertThrows(ResponseStatusException.class, () -> controller.page(user, new ExtendedModelMap()));
    }

    private Authentication admin() {
        Authentication admin = mock(Authentication.class);
        when(admin.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))).when(admin).getAuthorities();
        return admin;
    }
}
