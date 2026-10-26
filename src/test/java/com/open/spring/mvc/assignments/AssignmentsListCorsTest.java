package com.open.spring.mvc.assignments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import com.open.spring.mvc.S3uploads.FileHandler;
import com.open.spring.mvc.bathroom.TinkleJPARepository;
import com.open.spring.mvc.groups.GroupsJpaRepository;
import com.open.spring.mvc.person.PersonApiController;
import com.open.spring.mvc.person.PersonDetailsService;
import com.open.spring.mvc.person.PersonJpaRepository;
import com.open.spring.mvc.person.PersonService;
import com.open.spring.security.JwtAuthenticationEntryPoint;
import com.open.spring.security.JwtRequestFilter;
import com.open.spring.security.RateLimitFilter;
import com.open.spring.security.SecurityConfig;
import com.open.spring.system.kit.FeatureOverrideStore;
import com.open.spring.system.kit.KitCatalog;
import com.open.spring.system.kit.KitFeatures;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * GET /api/assignments used to fall through to PersonApiController's GET /api/{sid}, whose
 * {@code @CrossOrigin("*")} clashed with the global allowCredentials=true and threw a 500.
 * Both controllers are loaded so the routing that caused it is exercised.
 */
@WebMvcTest(controllers = {AssignmentsApiController.class, PersonApiController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RateLimitFilter.class, AssignmentsListCorsTest.Beans.class})
@TestPropertySource(properties = "kit.cors-origins=http://localhost:4500")
class AssignmentsListCorsTest {

    @TestConfiguration
    static class Beans {
        @Bean
        FeatureOverrideStore featureOverrideStore() {
            return new FeatureOverrideStore.InMemory();
        }

        @Bean
        KitFeatures kitFeatures(org.springframework.beans.factory.ObjectProvider<FeatureOverrideStore> store) {
            return new KitFeatures(KitCatalog.load(), "education", "", key -> null, store, event -> { });
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean JwtRequestFilter jwtRequestFilter;
    @MockitoBean GroupsJpaRepository groupRepo;
    @MockitoBean AssignmentJpaRepository assignmentRepo;
    @MockitoBean AssignmentSubmissionJPA submissionRepo;
    @MockitoBean PersonJpaRepository personRepo;
    @MockitoBean FileHandler fileHandler;
    @MockitoBean JdbcTemplate jdbcTemplate;
    @MockitoBean AssignmentRubricService rubricService;
    @MockitoBean AssignmentAuthorizationService assignmentAuthorizationService;
    @MockitoBean PlatformTransactionManager transactionManager;
    @MockitoBean AssignmentCreatorSyncService assignmentCreatorSyncService;
    @MockitoBean AssignmentCourseSyncService assignmentCourseSyncService;
    @MockitoBean PersonDetailsService personDetailsService;
    @MockitoBean PersonService personService;
    @MockitoBean TinkleJPARepository tinkleRepository;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtRequestFilter).doFilter(any(), any(), any());
        Assignment a = new Assignment();
        a.setName("Unit 1");
        when(assignmentRepo.findAll()).thenReturn(List.of(a));
    }

    @Test
    @WithMockUser(username = "teacher", authorities = {"ROLE_USER", "ROLE_TEACHER"})
    void teacherListsAssignmentsWithoutTrailingSlash() throws Exception {
        mvc.perform(get("/api/assignments")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Unit 1"));
    }

    @Test
    @WithMockUser(username = "teacher", authorities = {"ROLE_USER", "ROLE_TEACHER"})
    void teacherListsAssignmentsWithTrailingSlashFromAllowedOrigin() throws Exception {
        mvc.perform(get("/api/assignments/").header("Origin", "http://localhost:4500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Unit 1"));
    }

    @Test
    @WithMockUser(username = "teacher", authorities = {"ROLE_USER", "ROLE_TEACHER"})
    void singleSegmentApiPathNoLongerThrows() throws Exception {
        mvc.perform(get("/api/some-barcode")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "teacher", authorities = {"ROLE_USER", "ROLE_TEACHER"})
    void unlistedOriginIsRejectedByGlobalConfig() throws Exception {
        mvc.perform(get("/api/assignments").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    /** CORS comes only from kit.cors-origins; per-controller @CrossOrigin overrides are not allowed. */
    @Test
    void noControllerDeclaresCrossOrigin() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readAllLines(p).stream().map(String::trim)
                                    .anyMatch(l -> l.startsWith("@CrossOrigin"));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertThat(offenders).as("@CrossOrigin found; use CORS_ORIGINS instead").isEmpty();
        }
    }
}
