package com.open.spring.system.kit;

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

import com.open.spring.security.JwtAuthenticationEntryPoint;
import com.open.spring.security.JwtRequestFilter;
import com.open.spring.security.RateLimitFilter;
import com.open.spring.security.SecurityConfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/** /api/kit is public; /api/kit/admin is ROLE_ADMIN only, through the real API security chain. */
@WebMvcTest(controllers = KitApiController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RateLimitFilter.class, KitApiSecurityTest.Beans.class})
@TestPropertySource(properties = "kit.cors-origins=http://localhost:4500")
class KitApiSecurityTest {

    @TestConfiguration
    static class Beans {
        @Bean
        FeatureOverrideStore featureOverrideStore() {
            return new FeatureOverrideStore.InMemory();
        }

        @Bean
        KitFeatures kitFeatures(org.springframework.beans.factory.ObjectProvider<FeatureOverrideStore> store) {
            return new KitFeatures(KitCatalog.load(), "business", "", key -> null, store, event -> { });
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtRequestFilter jwtRequestFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtRequestFilter).doFilter(any(), any(), any());
    }

    @Test
    void kitIsPublic() throws Exception {
        mvc.perform(get("/api/kit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kit").value("business"))
                .andExpect(jsonPath("$.roles.staff.label").value("Manager"));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/kit/admin")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/kit/admin/features").contentType(MediaType.APPLICATION_JSON)
                .content("{\"features\":{\"finance-sim\":true}}")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "teacher", authorities = {"ROLE_USER", "ROLE_TEACHER"})
    void teacherGets403() throws Exception {
        mvc.perform(get("/api/kit/admin")).andExpect(status().isForbidden());
        mvc.perform(put("/api/kit/admin/features").contentType(MediaType.APPLICATION_JSON)
                .content("{\"features\":{\"finance-sim\":true}}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/kit/admin/features/finance-sim")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student", authorities = {"ROLE_USER", "ROLE_STUDENT"})
    void studentGets403() throws Exception {
        mvc.perform(get("/api/kit/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_USER", "ROLE_ADMIN"})
    void adminCanReadAndChangeFeatures() throws Exception {
        mvc.perform(get("/api/kit/admin")).andExpect(status().isOk())
                .andExpect(jsonPath("$.feature_details[0].source").value("kit"));
        mvc.perform(put("/api/kit/admin/features").contentType(MediaType.APPLICATION_JSON)
                .content("{\"features\":{\"finance-sim\":true}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.features[?(@ == 'finance-sim')]").exists());
        mvc.perform(put("/api/kit/admin/features").contentType(MediaType.APPLICATION_JSON)
                .content("{\"features\":{\"core\":false}}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/kit/admin/features/finance-sim")).andExpect(status().isOk())
                .andExpect(jsonPath("$.features[?(@ == 'finance-sim')]").doesNotExist());
    }
}
