package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.open.spring.mvc.assignments.AssignmentJpaRepository;
import com.open.spring.mvc.assignments.AssignmentSyncAccountProvisioner;
import com.open.spring.mvc.bank.BankService;
import com.open.spring.mvc.groups.CourseGroupProperties;
import com.open.spring.mvc.jokes.Jokes;
import com.open.spring.mvc.jokes.JokesJpaRepository;
import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonDetailsService;
import com.open.spring.mvc.person.PersonJpaRepository;
import com.open.spring.mvc.person.PersonRole;
import com.open.spring.mvc.person.PersonRoleJpaRepository;
import com.open.spring.mvc.stats.Stats;
import com.open.spring.mvc.stats.StatsRepository;
import com.open.spring.system.ModelInit;

/**
 * The kit round trip on a real SQLite database with the real store and seeders: rows survive every
 * switch, seeders only fill empty tables, and overrides and the stored kit commit together.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaFeatureOverrideStore.class)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create", "kit.admin-email=admin@example.com"})
class KitRoundTripPersistenceTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        String file = Files.createTempDirectory("kit-round-trip").resolve("test.db").toString();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file);
    }

    // Beans ModelInit needs that are not part of the seeders exercised here.
    @MockitoBean PersonDetailsService personDetailsService;
    @MockitoBean BankService bankService;
    @MockitoBean AssignmentSyncAccountProvisioner provisioner;
    @MockitoBean CourseGroupProperties courseGroups;
    @MockitoBean KitFeatures unusedKitFeaturesBean;

    @Autowired AutowireCapableBeanFactory beanFactory;
    @Autowired JpaFeatureOverrideStore store;
    @Autowired FeatureOverrideRepository overrideRows;
    @Autowired ActiveKitRepository kitRows;
    @Autowired JokesJpaRepository jokes;
    @Autowired AssignmentJpaRepository assignments;
    @Autowired StatsRepository stats;
    @Autowired PersonJpaRepository people;
    @Autowired PersonRoleJpaRepository roles;

    private final List<String> announced = new ArrayList<>();

    private KitFeatures features;

    @BeforeEach
    void clean() {
        store.forgetKit();
        jokes.deleteAll();
        assignments.deleteAll();
        stats.deleteAll();
        people.deleteAll();
        features = kit("vanilla");
    }

    /** A KitFeatures wired to the real ModelInit seeders; a new one over the same database is a restart. */
    private KitFeatures kit(String env) {
        ModelInit seeders = beanFactory.createBean(ModelInit.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", store);
        KitFeatures result = new KitFeatures(KitCatalog.load(), env, "", key -> null,
                beans.getBeanProvider(FeatureOverrideStore.class), event -> {
                    FeatureEnabledEvent enabled = (FeatureEnabledEvent) event;
                    announced.add(enabled.featureId());
                    seeders.onFeatureEnabled(enabled);
                });
        ReflectionTestUtils.setField(seeders, "kitFeatures", result);
        result.reloadOverrides();
        return result;
    }

    private PersonRole role(String name) {
        PersonRole found = roles.findByName(name);
        return found != null ? found : roles.save(new PersonRole(name));
    }

    private Person account(String uid) {
        Person person = new Person();
        person.setUid(uid);
        person.setEmail(uid + "@example.com");
        person.setName("Account " + uid);
        person.setPassword("Encoded-Password-1!");
        person.setRoles(new ArrayList<>(List.of(role("ROLE_USER"), role("ROLE_TEACHER"))));
        return people.save(person);
    }

    private List<String> roleNames(String uid) {
        return new org.springframework.transaction.support.TransactionTemplate(
                beanFactory.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .execute(status -> people.findByUid(uid).getRoles().stream().map(PersonRole::getName).sorted().toList());
    }

    @Test
    void roundTripKeepsAccountsAndDataAndSeedsOnlyEmptyTables() {
        Person staff = account("editor");
        long staffId = staff.getId();
        List<String> staffRoles = roleNames("editor");
        assertEquals(List.of("ROLE_TEACHER", "ROLE_USER"), staffRoles);
        assertEquals(0, jokes.count());

        // vanilla -> add a feature: its empty table gets seeded, plus a row of our own.
        features.applyOverrides(Map.of("examples", true), "admin");
        long seededJokes = jokes.count();
        assertTrue(seededJokes > 0);
        jokes.save(new Jokes(null, "mine", 0, 0));

        // -> education: override cleared, seeders run for newly enabled features.
        assertEquals(0, assignments.count());
        features.applyKit("education", "admin");
        assertFalse(features.isEnabled("examples"));
        assertEquals(0, overrideRows.count());
        long seededAssignments = assignments.count();
        assertTrue(seededAssignments > 0);
        long seededStats = stats.count();
        assertTrue(seededStats > 0);

        // -> business -> vanilla -> education: nothing is reseeded or removed.
        features.applyKit("business", "admin");
        assertFalse(features.isEnabled("assignments"));
        features.applyKit("vanilla", "admin");
        assertEquals(List.of("core"), features.getEnabled());
        assertEquals(seededAssignments, assignments.count());
        assertEquals(seededStats, stats.count());
        assertEquals(seededJokes + 1, jokes.count());

        announced.clear();
        features.applyKit("education", "admin");
        assertTrue(announced.contains("assignments"));
        assertEquals(seededAssignments, assignments.count());
        assertEquals(seededStats, stats.count());
        assertEquals(seededJokes + 1, jokes.count());
        assertEquals(1, jokes.findByJokeIgnoreCase("mine").size());

        // The account never changed: same row, uid and internal roles; only the label did.
        Person after = people.findByUid("editor");
        assertEquals(staffId, after.getId());
        assertEquals(staff.getEmail(), after.getEmail());
        assertEquals(staff.getPassword(), after.getPassword());
        assertEquals(staff.getTokenVersion(), after.getTokenVersion()); // JWTs stay valid
        assertEquals(staffRoles, roleNames("editor"));
        assertEquals("Teacher", features.authorityLabel("ROLE_TEACHER"));
        assertEquals(1, people.count()); // a switch creates no accounts
    }

    @Test
    void aTableThatAlreadyHasRowsIsNotSeeded() {
        stats.save(new Stats(null, "someone", "frontend", 1, Boolean.TRUE, 1.0, 0.5));
        features.applyKit("business", "admin");
        assertTrue(announced.contains("analytics"));
        assertEquals(1, stats.count());
    }

    @Test
    void theKitAndItsAuditSurviveARestartInTheDatabase() {
        features.applyKit("business", "alice");
        features.applyOverrides(Map.of("finance-sim", true), "alice");

        ActiveKit row = kitRows.findById(ActiveKit.SINGLE_ROW).orElseThrow();
        assertEquals("business", row.getKitId());
        assertEquals("alice", row.getUpdatedBy());
        assertNotNull(row.getUpdatedAt());

        KitFeatures restarted = kit("education");
        assertEquals("business", restarted.getKit());
        assertEquals("admin", restarted.kitSource());
        assertTrue(restarted.isEnabled("finance-sim"));
        assertTrue(restarted.isCustomized());
    }

    @Test
    void applyingAndForgettingAKitReplaceTheRows() {
        features.applyOverrides(Map.of("finance-sim", true, "calendar", true), "admin");
        assertEquals(2, overrideRows.count());

        features.applyKit("education", "admin");
        features.applyKit("business", "admin"); // replaces, never a second row
        assertEquals(1, kitRows.count());
        assertEquals(0, overrideRows.count());

        features.applyOverrides(Map.of("hallpass", true), "admin");
        features.forgetKit("admin");
        assertEquals(0, kitRows.count());
        assertEquals(0, overrideRows.count());
        assertEquals("vanilla", features.getKit());
        assertEquals("env", features.kitSource());
    }

    @Test
    void aFreshDatabaseWithoutTheTablesUsesTheEnvironmentAndIsNotDegraded() {
        org.springframework.jdbc.core.JdbcTemplate jdbc = beanFactory.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        jdbc.execute("DROP TABLE active_kit");
        jdbc.execute("DROP TABLE feature_override");

        JpaFeatureOverrideStore fresh = beanFactory.createBean(JpaFeatureOverrideStore.class); // has not checked the tables yet
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", fresh);
        KitFeatures f = new KitFeatures(KitCatalog.load(), "education", "", key -> null,
                beans.getBeanProvider(FeatureOverrideStore.class), event -> { });
        f.reloadOverrides();

        assertFalse(f.isDegraded());
        assertEquals("education", f.getKit());
        assertEquals("env", f.kitSource());
        assertEquals(0, kitRows.count()); // the tables exist again
        assertEquals(0, overrideRows.count());
    }

    @Test
    void aFailureDuringApplyLeavesTheStoredStateUntouched() {
        features.applyOverrides(Map.of("calendar", true), "admin");
        ActiveKit invalid = new ActiveKit(null, "admin", null); // kit_id is NOT NULL
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> store.applyKit(invalid));
        assertEquals(1, overrideRows.count());
        assertEquals(0, kitRows.count());
    }
}
