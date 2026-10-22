package com.open.spring.system.kit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The enabled kit features for this run, resolved once at startup from KIT and FEATURES
 * (see {@link KitResolver}) against the bundled catalog (see {@link KitCatalog}).
 *
 * Ask it by feature id, by class (the catalog maps packages to features) or by request path.
 * Templates use it as {@code ${@kitFeatures.pathEnabled('/mvc/bank/read')}}.
 */
@Component("kitFeatures")
public class KitFeatures {

    private static final Logger log = LoggerFactory.getLogger(KitFeatures.class);

    private final KitCatalog catalog;
    private final KitResolver.Resolution resolution;
    private final Set<String> enabled;

    public KitFeatures(Environment environment) {
        this(KitCatalog.load(), environment.getProperty("kit.preset", ""), environment.getProperty("kit.features", ""));
    }

    public KitFeatures(KitCatalog catalog, String kit, String features) {
        this.catalog = catalog;
        this.resolution = KitResolver.resolve(catalog, kit, features);
        this.enabled = Set.copyOf(resolution.enabled());
        logResolution(features);
    }

    private void logResolution(String features) {
        KitCatalog.Kit kit = catalog.kits().get(resolution.kit());
        String kitName = kit == null ? "every feature (KIT unset)" : kit.name();
        log.info("Kit '{}' ({}){}: {} of {} features enabled", resolution.kit(), kitName,
                resolution.customized() ? " customized by FEATURES=" + features : "",
                enabled.size(), catalog.features().size());
        for (KitCatalog.Feature feature : catalog.features().values()) {
            log.info("  [{}] {} - {} ({}; labels: {})", isEnabled(feature.id()) ? "on " : "off",
                    feature.id(), feature.name(), feature.category(), String.join(", ", feature.labels()));
        }
    }

    public KitCatalog catalog() {
        return catalog;
    }

    /** Kit id from KIT, or "default" when unset. */
    public String getKit() {
        return resolution.kit();
    }

    /** True when FEATURES changed the kit's preset. */
    public boolean isCustomized() {
        return resolution.customized();
    }

    /** Enabled feature ids in catalog order. */
    public List<String> getEnabled() {
        return resolution.enabled();
    }

    public boolean isEnabled(String featureId) {
        return enabled.contains(featureId);
    }

    /** Whether the feature owning this class (by package) is enabled; classes outside the app are always enabled. */
    public boolean isEnabledFor(Class<?> type) {
        return featureFor(type) == null || isEnabled(featureFor(type));
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

    /** Body of GET /api/kit. "features" keeps its original shape (enabled ids); details are additive. */
    public Map<String, Object> describe() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kit", resolution.kit());
        KitCatalog.Kit kit = catalog.kits().get(resolution.kit());
        body.put("name", kit == null ? "All features" : kit.name());
        body.put("customized", resolution.customized());
        body.put("features", resolution.enabled());
        Map<String, Object> details = new LinkedHashMap<>();
        for (KitCatalog.Feature feature : catalog.features().values()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", feature.name());
            d.put("category", feature.category());
            d.put("labels", feature.labels());
            d.put("enabled", isEnabled(feature.id()));
            details.put(feature.id(), d);
        }
        body.put("catalog", details);
        return body;
    }
}
