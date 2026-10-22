package com.open.spring.system.kit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The kit feature catalog, read from {@code src/main/resources/kits/catalog.json}.
 *
 * The catalog is the single source of truth for which Java packages and URL path prefixes
 * belong to which feature, and which features each kit enables. Everything else (the request
 * filter, scheduled-job and startup-task gating, seeding, admin nav) asks this class.
 *
 * Parsing is strict: duplicate package or path claims, malformed paths and kit presets that
 * name unknown features fail with an {@link IllegalStateException}, so a broken catalog stops
 * the app at startup instead of silently leaving something ungated.
 */
public final class KitCatalog {

    public static final String RESOURCE = "kits/catalog.json";
    /** Catalog package names are relative to this package. */
    public static final String BASE_PACKAGE = "com.open.spring";
    public static final String CORE = "core";
    public static final String ALL = "*";

    /** {@code setup} is the canonical setup guide (summary, settings, steps, verify), kept as JSON. */
    public record Feature(String id, String name, String description, String category, List<String> labels,
                          boolean alwaysOn, List<String> packages, List<String> paths, JsonNode setup) { }

    public record Category(String id, String name, String description) { }

    /** Canonical roles (admin, staff, member) with their label and seed account uid for this kit. */
    public record Role(String id, String label, String seedUid) { }

    public record Kit(String id, String name, String description, List<String> features,
                      Map<String, Role> roles, String signupRole) { }

    private final Map<String, Category> categories;
    private final Map<String, String> labels;
    private final Map<String, Feature> features;
    private final Map<String, Kit> kits;
    private final Map<String, String> packageOwners; // relative package -> feature id
    private final Map<String, String> pathOwners;    // path prefix -> feature id
    private final Map<String, List<String>> roleAuthorities; // canonical role -> Spring authorities

    private KitCatalog(Map<String, Category> categories, Map<String, String> labels, Map<String, Feature> features, Map<String, Kit> kits,
                       Map<String, String> packageOwners, Map<String, String> pathOwners,
                       Map<String, List<String>> roleAuthorities) {
        this.categories = Collections.unmodifiableMap(categories);
        this.labels = Collections.unmodifiableMap(labels);
        this.features = Collections.unmodifiableMap(features);
        this.kits = Collections.unmodifiableMap(kits);
        this.packageOwners = Collections.unmodifiableMap(packageOwners);
        this.pathOwners = Collections.unmodifiableMap(pathOwners);
        this.roleAuthorities = Collections.unmodifiableMap(roleAuthorities);
    }

    /** Loads the catalog bundled with the application. */
    public static KitCatalog load() {
        try (InputStream in = KitCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Kit catalog not found on the classpath: " + RESOURCE);
            }
            return parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read kit catalog " + RESOURCE + ": " + e.getMessage(), e);
        }
    }

    public static KitCatalog parse(InputStream in) throws IOException {
        JsonNode root = new ObjectMapper().readTree(in);
        Map<String, Feature> features = new LinkedHashMap<>();
        Map<String, String> packageOwners = new LinkedHashMap<>();
        Map<String, String> pathOwners = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        Map<String, Category> categories = new LinkedHashMap<>();
        root.path("categories").fields().forEachRemaining(entry -> categories.put(entry.getKey(), new Category(
                entry.getKey(), entry.getValue().path("name").asText(""), entry.getValue().path("description").asText(""))));
        Map<String, String> labels = new LinkedHashMap<>();
        root.path("labels").fields().forEachRemaining(entry -> labels.put(entry.getKey(), entry.getValue().asText("")));

        JsonNode featureNodes = root.path("features");
        featureNodes.fields().forEachRemaining(entry -> {
            String id = entry.getKey();
            JsonNode node = entry.getValue();
            JsonNode spring = node.path("spring");
            List<String> packages = strings(spring.path("packages"));
            List<String> paths = strings(spring.path("paths"));
            for (String pkg : packages) {
                if (pkg.isBlank() || pkg.startsWith(".") || pkg.endsWith(".") || pkg.contains("/")) {
                    errors.add("feature '" + id + "': bad package '" + pkg + "' (use e.g. mvc.jokes)");
                }
                String previous = packageOwners.putIfAbsent(pkg, id);
                if (previous != null) {
                    errors.add("package '" + pkg + "' is claimed by both '" + previous + "' and '" + id + "'");
                }
            }
            for (String path : paths) {
                if (!path.startsWith("/") || (path.length() > 1 && path.endsWith("/")) || path.equals("/")) {
                    errors.add("feature '" + id + "': bad path prefix '" + path
                            + "' (must start with / and not end with /)");
                }
                String previous = pathOwners.putIfAbsent(path, id);
                if (previous != null) {
                    errors.add("path prefix '" + path + "' is claimed by both '" + previous + "' and '" + id + "'");
                }
            }
            String category = node.path("category").asText("");
            if (!categories.isEmpty() && !categories.containsKey(category)) {
                errors.add("feature '" + id + "': unknown category '" + category + "'");
            }
            List<String> featureLabels = strings(node.path("labels"));
            for (String label : featureLabels) {
                if (!labels.containsKey(label)) {
                    errors.add("feature '" + id + "': unknown label '" + label + "'");
                }
            }
            features.put(id, new Feature(id, node.path("name").asText(""), node.path("description").asText(""),
                    category, featureLabels, node.path("always_on").asBoolean(false), packages, paths,
                    node.path("setup")));
        });
        if (!features.containsKey(CORE)) {
            errors.add("the catalog must define the '" + CORE + "' feature");
        }

        Map<String, Kit> kits = new LinkedHashMap<>();
        root.path("kits").fields().forEachRemaining(entry -> {
            String id = entry.getKey();
            JsonNode node = entry.getValue();
            List<String> members = strings(node.path("features"));
            for (String member : members) {
                if (!ALL.equals(member) && !features.containsKey(member)) {
                    errors.add("kit '" + id + "' names unknown feature '" + member + "'");
                }
            }
            Map<String, Role> roles = new LinkedHashMap<>();
            node.path("roles").fields().forEachRemaining(role -> roles.put(role.getKey(), new Role(role.getKey(),
                    role.getValue().path("label").asText(role.getKey()), role.getValue().path("seed_uid").asText(""))));
            String signupRole = node.path("signup_role").asText("member");
            if (!roles.isEmpty() && !roles.containsKey(signupRole)) {
                errors.add("kit '" + id + "': signup_role '" + signupRole + "' is not one of its roles");
            }
            kits.put(id, new Kit(id, node.path("name").asText(""), node.path("description").asText(""), members,
                    Collections.unmodifiableMap(roles), signupRole));
        });
        if (kits.isEmpty()) {
            errors.add("the catalog defines no kits");
        }

        Map<String, List<String>> roleAuthorities = new LinkedHashMap<>();
        root.path("spring").path("roles").fields().forEachRemaining(entry ->
                roleAuthorities.put(entry.getKey(), strings(entry.getValue())));

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid kit catalog " + RESOURCE + ":\n  - " + String.join("\n  - ", errors));
        }
        return new KitCatalog(categories, labels, features, kits, packageOwners, pathOwners, roleAuthorities);
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText().trim()));
        return List.copyOf(values);
    }

    public Map<String, Category> categories() {
        return categories;
    }

    /** Label id -> description. */
    public Map<String, String> labels() {
        return labels;
    }

    public Map<String, Feature> features() {
        return features;
    }

    public Map<String, Kit> kits() {
        return kits;
    }

    public Map<String, String> packageOwners() {
        return packageOwners;
    }

    public Map<String, String> pathOwners() {
        return pathOwners;
    }

    /**
     * Spring authorities granted for a canonical role (admin, staff, member), from the catalog's
     * spring.roles block. Internal role names never change; kits only relabel them.
     */
    public List<String> authoritiesFor(String canonicalRole) {
        return roleAuthorities.getOrDefault(canonicalRole, List.of("ROLE_USER"));
    }

    public Map<String, List<String>> roleAuthorities() {
        return roleAuthorities;
    }

    /** Expands a kit preset ("*" means every feature) into feature ids, in catalog order. */
    public List<String> expand(Kit kit) {
        if (kit.features().contains(ALL)) {
            return List.copyOf(features.keySet());
        }
        return features.keySet().stream().filter(kit.features()::contains).toList();
    }

    /**
     * Feature that owns a class, by the longest catalog package containing it.
     * Classes outside {@value #BASE_PACKAGE} return null (not ours, never gated);
     * classes in packages the catalog does not list belong to core.
     */
    public String featureForClassName(String className) {
        if (className == null || !className.startsWith(BASE_PACKAGE + ".")) {
            return null;
        }
        String relative = className.substring(BASE_PACKAGE.length() + 1);
        int lastDot = relative.lastIndexOf('.');
        String pkg = lastDot < 0 ? "" : relative.substring(0, lastDot);
        String best = null;
        for (String candidate : packageOwners.keySet()) {
            if ((pkg.equals(candidate) || pkg.startsWith(candidate + "."))
                    && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        return best == null ? CORE : packageOwners.get(best);
    }

    /** Feature that owns a request path, by the longest matching path prefix; null when unclaimed. */
    public String featureForPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String best = null;
        for (String prefix : pathOwners.keySet()) {
            if ((path.equals(prefix) || path.startsWith(prefix + "/"))
                    && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        return best == null ? null : pathOwners.get(best);
    }
}
