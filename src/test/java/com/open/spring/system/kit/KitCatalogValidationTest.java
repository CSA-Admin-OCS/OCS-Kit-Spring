package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guards src/main/resources/kits/catalog.json, the single source of truth for kit features.
 *
 * Fails when: the catalog does not parse strictly (duplicate package/path claims, unknown ids in
 * presets, unknown categories or labels); a listed package does not exist; an mvc package (or other top-level package) is not
 * claimed by any feature, core included; a controller endpoint is not covered by a path prefix of
 * its package's feature (so a new controller cannot silently bypass gating); or ids, names,
 * descriptions, categories, labels or kit presets drift from the canonical OCS-Kit/kits/catalog.json.
 *
 * Run with -Dkit.dump=true to print every endpoint with its owning feature.
 */
class KitCatalogValidationTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/open/spring");
    private final KitCatalog catalog = KitCatalog.load();

    @Test
    void listedPackagesExist() {
        List<String> missing = new ArrayList<>();
        for (String pkg : catalog.packageOwners().keySet()) {
            if (!SOURCE_ROOT.resolve(pkg.replace('.', '/')).toFile().isDirectory()) {
                missing.add(pkg + " (" + catalog.packageOwners().get(pkg) + ")");
            }
        }
        assertTrue(missing.isEmpty(), "catalog lists packages that do not exist under " + SOURCE_ROOT + ": " + missing);
    }

    @Test
    void everyPackageIsClaimed() {
        List<String> unclaimed = new ArrayList<>();
        for (File dir : listDirs(SOURCE_ROOT.toFile())) {
            if (dir.getName().equals("mvc")) {
                for (File sub : listDirs(dir)) {
                    claim("mvc." + sub.getName(), unclaimed);
                }
            } else {
                claim(dir.getName(), unclaimed);
            }
        }
        assertTrue(unclaimed.isEmpty(), "packages not mapped to any feature in kits/catalog.json "
                + "(add them to a feature's spring.packages, or to core): " + unclaimed);
    }

    private void claim(String pkg, List<String> unclaimed) {
        if (!catalog.packageOwners().containsKey(pkg)) {
            unclaimed.add(pkg);
        }
    }

    @Test
    void everyEndpointIsGatedByItsFeature() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, String> dump = new TreeMap<>();
        for (Map.Entry<Class<?>, List<String>> entry : endpoints().entrySet()) {
            Class<?> controller = entry.getKey();
            String owningPackage = claimedPackage(controller.getName());
            if (owningPackage == null) {
                problems.add(controller.getName() + ": package not claimed by any feature");
                continue;
            }
            String packageFeature = catalog.packageOwners().get(owningPackage);
            for (String path : entry.getValue()) {
                String pathFeature = catalog.featureForPath(path);
                dump.put(path + "  [" + controller.getSimpleName() + "]", String.valueOf(pathFeature));
                if (pathFeature == null) {
                    problems.add(path + " (" + controller.getSimpleName() + ", feature " + packageFeature
                            + "): no spring.paths prefix covers it");
                } else if (!pathFeature.equals(packageFeature) && !KitCatalog.CORE.equals(packageFeature)
                        && !underPrefixOf(path, packageFeature)) {
                    // A feature package may host another feature's endpoint only when that endpoint is a
                    // carve-out under one of its own prefixes (e.g. /api/submissions/ai-grade).
                    problems.add(path + " (" + controller.getSimpleName() + ") is in package feature '" + packageFeature
                            + "' but its path prefix belongs to '" + pathFeature + "'");
                }
            }
        }
        if (Boolean.getBoolean("kit.dump")) {
            dump.forEach((k, v) -> System.out.println("KIT-DUMP " + v + "\t" + k));
        }
        assertTrue(problems.isEmpty(), "endpoints not gated by their feature:\n  " + String.join("\n  ", problems));
    }

    @Test
    void featureIdsAndKitsMatchCanonicalCatalog() throws Exception {
        String override = System.getenv("OCS_KIT_CATALOG");
        File canonical = new File(override != null && !override.isBlank() ? override : "../OCS-Kit/kits/catalog.json");
        Assumptions.assumeTrue(canonical.isFile(),
                "canonical catalog not found at " + canonical + " (set OCS_KIT_CATALOG to check parity)");

        JsonNode expected;
        JsonNode actual;
        try (InputStream a = new FileInputStream(canonical);
             InputStream b = getClass().getClassLoader().getResourceAsStream(KitCatalog.RESOURCE)) {
            ObjectMapper mapper = new ObjectMapper();
            expected = mapper.readTree(a);
            actual = mapper.readTree(b);
        }
        assertEquals(fieldNames(expected.path("features")), fieldNames(actual.path("features")),
                "feature ids (and their order) differ from " + canonical);
        Iterator<Map.Entry<String, JsonNode>> it = expected.path("features").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> feature = it.next();
            JsonNode mine = actual.path("features").path(feature.getKey());
            feature.getValue().fields().forEachRemaining(field -> assertEquals(field.getValue(), mine.get(field.getKey()),
                    "feature '" + feature.getKey() + "' field '" + field.getKey() + "' differs from " + canonical));
        }
        assertEquals(expected.path("kits"), actual.path("kits"), "kits differ from " + canonical);
        assertEquals(expected.path("categories"), actual.path("categories"), "categories differ from " + canonical);
        assertEquals(expected.path("labels"), actual.path("labels"), "labels differ from " + canonical);
        assertEquals(expected.path("description"), actual.path("description"), "description differs from " + canonical);
    }

    @Test
    void strictParsingRejectsDuplicateClaimsAndUnknownIds() {
        String bad = "{\"features\":{\"core\":{\"always_on\":true,\"spring\":{\"packages\":[\"a\"],\"paths\":[\"/x\"]}},"
                + "\"f\":{\"spring\":{\"packages\":[\"a\"],\"paths\":[\"/x\"]}}},"
                + "\"kits\":{\"k\":{\"features\":[\"core\",\"nope\"]}}}";
        try {
            KitCatalog.parse(new java.io.ByteArrayInputStream(bad.getBytes()));
            fail("expected an invalid catalog to be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("package 'a'"), e.getMessage());
            assertTrue(e.getMessage().contains("path prefix '/x'"), e.getMessage());
            assertTrue(e.getMessage().contains("unknown feature 'nope'"), e.getMessage());
        } catch (Exception e) {
            fail(e);
        }
    }

    private boolean underPrefixOf(String path, String feature) {
        return catalog.features().get(feature).paths().stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private String claimedPackage(String className) {
        String relative = className.substring(KitCatalog.BASE_PACKAGE.length() + 1);
        String pkg = relative.contains(".") ? relative.substring(0, relative.lastIndexOf('.')) : "";
        String best = null;
        for (String candidate : catalog.packageOwners().keySet()) {
            if ((pkg.equals(candidate) || pkg.startsWith(candidate + "."))
                    && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        return best;
    }

    /** Every @Controller/@RestController under com.open.spring with its full mapped paths. */
    static Map<Class<?>, List<String>> endpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Map<Class<?>, List<String>> result = new LinkedHashMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(KitCatalog.BASE_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName(), false, KitCatalogValidationTest.class.getClassLoader());
            RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = typeMapping == null || typeMapping.path().length == 0 ? new String[] {""} : typeMapping.path();
            List<String> paths = new ArrayList<>();
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(type)) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] suffixes = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String suffix : suffixes) {
                        String path = join(prefix, suffix);
                        if (!paths.contains(path)) {
                            paths.add(path);
                        }
                    }
                }
            }
            if (!paths.isEmpty()) {
                result.put(type, paths);
            }
        }
        return result;
    }

    private static String join(String prefix, String suffix) {
        String path = slash(prefix) + slash(suffix);
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path.replace("//", "/");
    }

    private static String slash(String part) {
        String p = part.trim();
        return p.isEmpty() || p.startsWith("/") ? p : "/" + p;
    }

    private static List<File> listDirs(File dir) {
        File[] children = dir.listFiles(File::isDirectory);
        List<File> dirs = new ArrayList<>();
        if (children != null) {
            for (File child : children) {
                dirs.add(child);
            }
        }
        dirs.sort((a, b) -> a.getName().compareTo(b.getName()));
        return dirs;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
