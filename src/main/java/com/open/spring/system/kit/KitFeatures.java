package com.open.spring.system.kit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The effective kit features: KIT preset, then FEATURES, then admin overrides (feature_override
 * table). Everything that gates on features asks this bean at the moment it needs the answer,
 * so admin changes take effect without a restart.
 *
 * Ask by feature id, by class (the catalog maps packages to features) or by request path.
 * Templates use it as {@code ${@kitFeatures.pathEnabled('/mvc/bank/read')}}.
 */
@Component("kitFeatures")
public class KitFeatures {

    private static final Logger log = LoggerFactory.getLogger(KitFeatures.class);

    /** Kit whose role labels apply when KIT is unset (the platform's original classroom roles). */
    static final String FALLBACK_ROLE_KIT = "education";

    /** A startup task that may throw; deferred until its feature is enabled. */
    @FunctionalInterface
    public interface Task {
        void run() throws Exception;
    }

    private final KitCatalog catalog;
    private final KitResolver.Resolution resolution;
    private final Set<String> preset;
    private final Function<String, String> settings;
    private final ObjectProvider<FeatureOverrideStore> storeProvider;
    private final ApplicationEventPublisher events;
    private final Map<String, List<Task>> deferred = new ConcurrentHashMap<>();

    private volatile Map<String, FeatureOverride> overrides = Map.of();
    private volatile Set<String> enabled;

    @Autowired
    public KitFeatures(Environment environment, ObjectProvider<FeatureOverrideStore> store, ApplicationEventPublisher events) {
        this(KitCatalog.load(), environment.getProperty("kit.preset", ""), environment.getProperty("kit.features", ""),
                environment::getProperty, store, events);
    }

    /** For tests: settings maps a setting key (e.g. GEMINI_API_KEY) to its value or null. */
    public KitFeatures(KitCatalog catalog, String kit, String features, Function<String, String> settings,
                       ObjectProvider<FeatureOverrideStore> store, ApplicationEventPublisher events) {
        this.catalog = catalog;
        this.resolution = KitResolver.resolve(catalog, kit, features);
        KitCatalog.Kit presetKit = catalog.kits().get(resolution.kit());
        this.preset = Set.copyOf(presetKit == null ? catalog.features().keySet() : catalog.expand(presetKit));
        this.settings = settings;
        this.storeProvider = store;
        this.events = events;
        this.enabled = Set.copyOf(resolution.enabled());
        KitSeedAccounts.activate(KitSeedAccounts.forKit(catalog, roleKit()));
    }

    /** Spring authorities a new sign-up gets: the kit's signup_role (catalog spring.roles). */
    public List<String> signupAuthorities() {
        return catalog.authoritiesFor(roleKit().signupRole());
    }

    // ------------------------------------------------------------------ startup

    /** Loads admin overrides once the database is ready, then logs the effective features. */
    @EventListener(ContextRefreshedEvent.class)
    public void onStartup() {
        reloadOverrides();
        logState();
    }

    public synchronized void reloadOverrides() {
        FeatureOverrideStore store = storeProvider.getIfAvailable();
        if (store == null) {
            return;
        }
        Map<String, FeatureOverride> loaded = new LinkedHashMap<>();
        try {
            for (FeatureOverride override : store.loadAll()) {
                if (catalog.features().containsKey(override.getFeatureId())) {
                    loaded.put(override.getFeatureId(), override);
                } else {
                    log.warn("Ignoring admin override for unknown feature '{}'", override.getFeatureId());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not load admin feature overrides, using KIT/FEATURES only: {}", e.getMessage());
            return;
        }
        overrides = Collections.unmodifiableMap(loaded);
        recompute();
    }

    private void logState() {
        log.info("Kit '{}' ({}){}: {} of {} features enabled", resolution.kit(), kitName(),
                resolution.customized() ? " customized by FEATURES" : "", enabled.size(), catalog.features().size());
        for (KitCatalog.Feature feature : catalog.features().values()) {
            log.info("  [{}] {} - {} ({}; labels: {}; source: {})", isEnabled(feature.id()) ? "on " : "off",
                    feature.id(), feature.name(), feature.category(), String.join(", ", feature.labels()),
                    source(feature.id()));
        }
    }

    // ------------------------------------------------------------------ queries

    public KitCatalog catalog() {
        return catalog;
    }

    /** Kit id from KIT, or "default" when unset. */
    public String getKit() {
        return resolution.kit();
    }

    public String kitName() {
        KitCatalog.Kit kit = catalog.kits().get(resolution.kit());
        return kit == null ? "All features" : kit.name();
    }

    /** Enabled feature ids in catalog order. */
    public List<String> getEnabled() {
        Set<String> current = enabled;
        return catalog.features().keySet().stream().filter(current::contains).toList();
    }

    public boolean isEnabled(String featureId) {
        return enabled.contains(featureId);
    }

    /** Whether the feature owning this class (by package) is enabled; classes outside the app are always enabled. */
    public boolean isEnabledFor(Class<?> type) {
        String feature = featureFor(type);
        return feature == null || isEnabled(feature);
    }

    /** Feature owning a class by package, or null for classes outside com.open.spring. */
    public String featureFor(Class<?> type) {
        return catalog.featureForClassName(type.getName());
    }

    /** Whether a request path is served: false only when it falls under a disabled feature's prefix. */
    public boolean pathEnabled(String path) {
        return disabledFeatureForPath(path) == null;
    }

    /** The disabled feature owning this path, or null when the path is served. */
    public String disabledFeatureForPath(String path) {
        String feature = catalog.featureForPath(path);
        return feature == null || isEnabled(feature) ? null : feature;
    }

    /** Where a feature's current state comes from: "admin" override, "env" (FEATURES) or the "kit" preset. */
    public String source(String featureId) {
        if (overrides.containsKey(featureId)) {
            return "admin";
        }
        return resolution.enabled().contains(featureId) != preset.contains(featureId) ? "env" : "kit";
    }

    /** The kit whose role labels and seed uids apply (education's when KIT is unset). */
    public KitCatalog.Kit roleKit() {
        KitCatalog.Kit kit = catalog.kits().get(resolution.kit());
        if (kit == null || kit.roles().isEmpty()) {
            kit = catalog.kits().getOrDefault(FALLBACK_ROLE_KIT, catalog.kits().values().iterator().next());
        }
        return kit;
    }

    /** Label users see for a canonical role (admin, staff, member) in this kit. */
    public String roleLabel(String canonicalRole) {
        KitCatalog.Role role = roleKit().roles().get(canonicalRole);
        return role == null ? canonicalRole : role.label();
    }

    /** Label for a Spring authority such as ROLE_TEACHER, using the kit's role names. */
    public String authorityLabel(String authority) {
        for (Map.Entry<String, List<String>> entry : catalog.roleAuthorities().entrySet()) {
            List<String> authorities = entry.getValue();
            if (!authorities.isEmpty() && authorities.get(authorities.size() - 1).equals(authority)) {
                return roleLabel(entry.getKey());
            }
        }
        return authority.startsWith("ROLE_") ? authority.substring(5) : authority;
    }

    // ------------------------------------------------------------------ admin changes

    /**
     * Applies admin overrides, e.g. {"finance-sim": true, "hallpass": false}. All entries are
     * validated before anything is saved. Newly enabled features get their deferred startup
     * tasks run and a {@link FeatureEnabledEvent} (seed data).
     */
    public synchronized void applyOverrides(Map<String, Boolean> changes, String actor) {
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("No features given; send {\"features\": {\"<id>\": true|false}}");
        }
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            KitCatalog.Feature feature = requireFeature(change.getKey());
            if (change.getValue() == null) {
                throw new IllegalArgumentException("Feature '" + change.getKey() + "' needs true or false");
            }
            if (feature.alwaysOn() && !change.getValue()) {
                throw new IllegalArgumentException("Feature '" + feature.id() + "' is always on and cannot be disabled");
            }
        }
        FeatureOverrideStore store = requireStore();
        Map<String, FeatureOverride> updated = new LinkedHashMap<>(overrides);
        Instant now = Instant.now();
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            FeatureOverride override = new FeatureOverride(change.getKey(), change.getValue(), actor, now);
            store.save(override);
            updated.put(change.getKey(), override);
            log.info("Feature '{}' {} by {} at {}", change.getKey(), change.getValue() ? "enabled" : "disabled", actor, now);
        }
        commit(updated);
    }

    /** Removes an admin override so the feature falls back to KIT/FEATURES. */
    public synchronized void clearOverride(String featureId, String actor) {
        requireFeature(featureId);
        requireStore().delete(featureId);
        Map<String, FeatureOverride> updated = new LinkedHashMap<>(overrides);
        updated.remove(featureId);
        log.info("Admin override for feature '{}' cleared by {} at {}", featureId, actor, Instant.now());
        commit(updated);
    }

    private void commit(Map<String, FeatureOverride> updated) {
        Set<String> before = enabled;
        overrides = Collections.unmodifiableMap(updated);
        recompute();
        for (String id : getEnabled()) {
            if (!before.contains(id)) {
                runDeferred(id);
                if (events != null) {
                    events.publishEvent(new FeatureEnabledEvent(id));
                }
            }
        }
    }

    private void recompute() {
        Set<String> next = new LinkedHashSet<>(resolution.enabled());
        for (FeatureOverride override : overrides.values()) {
            if (override.isEnabled()) {
                next.add(override.getFeatureId());
            } else {
                next.remove(override.getFeatureId());
            }
        }
        catalog.features().values().stream().filter(KitCatalog.Feature::alwaysOn).forEach(f -> next.add(f.id()));
        enabled = Set.copyOf(next);
    }

    private KitCatalog.Feature requireFeature(String featureId) {
        KitCatalog.Feature feature = featureId == null ? null : catalog.features().get(featureId);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature '" + featureId + "'. Valid features: "
                    + String.join(", ", catalog.features().keySet()));
        }
        return feature;
    }

    private FeatureOverrideStore requireStore() {
        FeatureOverrideStore store = storeProvider.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("Feature overrides cannot be stored (no database)");
        }
        return store;
    }

    // ------------------------------------------------------------------ deferred startup tasks

    /** Runs a feature's startup task now, or later when an admin enables the feature. */
    public void runWhenEnabled(String featureId, String name, Task task) throws Exception {
        if (isEnabled(featureId)) {
            task.run();
            return;
        }
        log.info("Deferring startup task {}: feature '{}' is disabled", name, featureId);
        deferred.computeIfAbsent(featureId, id -> new CopyOnWriteArrayList<>()).add(task);
    }

    private void runDeferred(String featureId) {
        List<Task> tasks = deferred.remove(featureId);
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Deferred startup task for feature '{}' failed: {}", featureId, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ API payloads

    /** GET /api/kit: public, no setup details. */
    public Map<String, Object> describePublic() {
        Map<String, Object> body = header();
        List<Map<String, Object>> details = new ArrayList<>();
        for (KitCatalog.Feature feature : catalog.features().values()) {
            details.add(detail(feature));
        }
        body.put("feature_details", details);
        body.putAll(roles());
        return body;
    }

    /** GET /api/kit/admin: adds source, setup guide and readiness. Never includes setting values. */
    public Map<String, Object> describeAdmin() {
        Map<String, Object> body = header();
        List<Map<String, Object>> details = new ArrayList<>();
        for (KitCatalog.Feature feature : catalog.features().values()) {
            Map<String, Object> d = detail(feature);
            d.put("always_on", feature.alwaysOn());
            d.put("description", feature.description());
            d.put("source", source(feature.id()));
            FeatureOverride override = overrides.get(feature.id());
            if (override != null) {
                d.put("updated_by", override.getUpdatedBy());
                d.put("updated_at", override.getUpdatedAt() == null ? null : override.getUpdatedAt().toString());
            }
            Map<String, Object> setup = setup(feature.setup());
            d.put("setup", setup);
            d.put("ready", setup.get("ready"));
            setup.remove("ready");
            details.add(d);
        }
        body.put("feature_details", details);
        body.putAll(roles());
        return body;
    }

    private Map<String, Object> header() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kit", resolution.kit());
        body.put("kit_name", kitName());
        body.put("features", getEnabled());
        return body;
    }

    private Map<String, Object> detail(KitCatalog.Feature feature) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", feature.id());
        d.put("name", feature.name());
        d.put("category", feature.category());
        d.put("labels", feature.labels());
        d.put("enabled", isEnabled(feature.id()));
        return d;
    }

    private Map<String, Object> roles() {
        KitCatalog.Kit kit = roleKit();
        Map<String, Object> roles = new LinkedHashMap<>();
        for (KitCatalog.Role role : kit.roles().values()) {
            roles.put(role.id(), Map.of("label", role.label()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roles", roles);
        body.put("signup_role", kit.signupRole());
        return body;
    }

    /** Spring settings only, each reported as configured true/false; "ready" = all required ones configured. */
    private Map<String, Object> setup(JsonNode setup) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", setup.path("summary").asText(""));
        List<Map<String, Object>> items = new ArrayList<>();
        boolean ready = true;
        for (JsonNode setting : setup.path("settings")) {
            if (!"spring".equals(setting.path("service").asText())) {
                continue;
            }
            String key = setting.path("key").asText();
            String value = settings.apply(key);
            boolean configured = value != null && !value.isBlank();
            boolean required = setting.path("required").asBoolean(false);
            ready &= configured || !required;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("required", required);
            item.put("secret", setting.path("secret").asBoolean(false));
            item.put("configured", configured);
            item.put("help", setting.path("help").asText(""));
            for (String optional : List.of("get_it", "example")) {
                if (setting.hasNonNull(optional)) {
                    item.put(optional, setting.get(optional).asText());
                }
            }
            items.add(item);
        }
        result.put("settings", items);
        List<String> steps = new ArrayList<>();
        setup.path("steps").forEach(step -> steps.add(step.asText()));
        result.put("steps", steps);
        result.put("verify", setup.path("verify").asText(""));
        result.put("ready", ready);
        return result;
    }
}
