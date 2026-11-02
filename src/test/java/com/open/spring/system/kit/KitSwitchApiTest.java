package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.open.spring.security.JwtAuthenticationEntryPoint;
import com.open.spring.security.JwtRequestFilter;
import com.open.spring.security.RateLimitFilter;
import com.open.spring.security.SecurityConfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/** PUT and DELETE /api/kit/admin/kit through the real API security chain and feature gate. */
@WebMvcTest(controllers = KitApiController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RateLimitFilter.class, KitConfig.class,
        KitSwitchApiTest.Beans.class})
@TestPropertySource(properties = "kit.cors-origins=http://localhost:4500")
class KitSwitchApiTest {

    @TestConfiguration
    static class Beans {
        @Bean
        FeatureOverrideStore featureOverrideStore() {
            return new FeatureOverrideStore.InMemory();
        }

        @Bean
        KitFeatures kitFeatures(org.springframework.beans.factory.ObjectProvider<FeatureOverrideStore> store) {
            return new KitFeatures(KitCatalog.load(), "vanilla", "", key -> null, store, event -> { });
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    KitFeatures features;

    @Autowired
    FeatureOverrideStore store;

    @MockitoBean
    JwtRequestFilter jwtRequestFilter;

    @BeforeEach
    void reset() throws Exception {
        features.forgetKit("test");
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtRequestFilter).doFilter(any(), any(), any());
    }

    private ResultActions putKit(String body) throws Exception {
        return mvc.perform(put("/api/kit/admin/kit").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void anonymousGets401AndNothingChanges() throws Exception {
        putKit("{\"kit\":\"education\"}").andExpect(status().isUnauthorized());
        putKit("{\"kit\":\"nope\"}").andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/kit/admin/kit")).andExpect(status().isUnauthorized());
        assertEquals("vanilla", features.getKit());
    }

    @Test
    @WithMockUser(username = "teacher", authorities = {"ROLE_USER", "ROLE_TEACHER"})
    void staffGets403EvenWithABadBody() throws Exception {
        putKit("{\"kit\":\"education\"}").andExpect(status().isForbidden());
        putKit("{\"kit\":\"nope\"}").andExpect(status().isForbidden());
        putKit("not json").andExpect(status().isForbidden());
        mvc.perform(delete("/api/kit/admin/kit")).andExpect(status().isForbidden());
        assertTrue(store.loadActiveKit().isEmpty());
    }

    @Test
    @WithMockUser(username = "student", authorities = {"ROLE_USER", "ROLE_STUDENT"})
    void memberGets403() throws Exception {
        putKit("{\"kit\":\"education\"}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/kit/admin/kit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_USER", "ROLE_ADMIN"})
    void adminBadBodiesGet400() throws Exception {
        for (String body : new String[] {"{\"kit\":\"nope\"}", "{\"kit\":\"default\"}", "{\"kit\":5}", "{\"kit\":null}", "{}", "not json", ""}) {
            putKit(body).andExpect(status().isBadRequest());
        }
        assertTrue(store.loadActiveKit().isEmpty());
    }

    @Test
    @WithMockUser(username = "boss", authorities = {"ROLE_USER", "ROLE_ADMIN"})
    void adminAppliesAKitAndGetsTheAdminPayload() throws Exception {
        putKit("{\"kit\":\"business\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.kit").value("business"))
                .andExpect(jsonPath("$.kit_name").value("OCS Business"))
                .andExpect(jsonPath("$.kit_source").value("admin"))
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.roles.staff.label").value("Manager"))
                .andExpect(jsonPath("$.signup_role").value("member"))
                .andExpect(jsonPath("$.features[?(@ == 'calendar')]").exists())
                .andExpect(jsonPath("$.feature_details[0].source").value("kit"))
                .andExpect(jsonPath("$.presets.education.roles.staff.label").value("Teacher"))
                .andExpect(jsonPath("$.presets.vanilla.features[0]").value("core"));
        assertEquals("boss", store.loadActiveKit().orElseThrow().getUpdatedBy());

        mvc.perform(get("/api/kit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kit").value("business"))
                .andExpect(jsonPath("$.kit_source").value("admin"))
                .andExpect(jsonPath("$.roles.member.label").value("Member"))
                .andExpect(jsonPath("$.presets").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_USER", "ROLE_ADMIN"})
    void customizingAndForgettingThroughTheApi() throws Exception {
        putKit("{\"kit\":\"education\"}").andExpect(status().isOk());
        mvc.perform(put("/api/kit/admin/features").contentType(MediaType.APPLICATION_JSON)
                .content("{\"features\":{\"finance-sim\":true}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kit").value("education"))
                .andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.feature_details[?(@.id == 'finance-sim')].source").value("admin"));

        mvc.perform(delete("/api/kit/admin/kit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kit").value("vanilla"))
                .andExpect(jsonPath("$.kit_source").value("env"))
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.roles.staff.label").value("Editor"))
                .andExpect(jsonPath("$.features.length()").value(1));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_USER", "ROLE_ADMIN"})
    void theFeatureGateFollowsASwitch() throws Exception {
        mvc.perform(get("/api/calendar/events")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.feature").value("calendar"));
        putKit("{\"kit\":\"business\"}").andExpect(status().isOk());
        // No calendar controller in this slice: the gate lets it through, so the 404 has no feature.
        mvc.perform(get("/api/calendar/events")).andExpect(jsonPath("$.feature").doesNotExist());
        mvc.perform(get("/api/kit/admin")).andExpect(status().isOk());
    }
}
