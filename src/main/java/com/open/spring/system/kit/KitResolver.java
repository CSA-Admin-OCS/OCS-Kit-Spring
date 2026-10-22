package com.open.spring.system.kit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the KIT and FEATURES settings into the set of enabled feature ids.
 *
 * <pre>
 * KIT       preset to start from (vanilla, education, business); unset = every feature
 *           (reported as kit "default"; the behaviour before kits existed)
 * FEATURES  optional, comma separated:
 *             +id / -id   add or remove relative to the KIT preset   e.g. +finance-sim,-hallpass
 *             id,id,...   an explicit list replacing the preset       e.g. lessons,code-runner
 *             *           every feature
 * </pre>
 * Plain ids and +/- entries can be mixed: any plain id (or *) makes the list explicit, and the
 * entries then apply left to right. core is always on. Unknown ids fail fast.
 */
public final class KitResolver {

    /** Kit name reported when KIT is unset: every feature is enabled. */
    public static final String DEFAULT_KIT = "default";

    public record Resolution(String kit, List<String> enabled, boolean customized) { }

    private KitResolver() { }

    public static Resolution resolve(KitCatalog catalog, String kitSetting, String featuresSetting) {
        String kitId = kitSetting == null ? "" : kitSetting.trim().toLowerCase();
        List<String> preset;
        if (kitId.isEmpty()) {
            kitId = DEFAULT_KIT;
            preset = List.copyOf(catalog.features().keySet());
        } else {
            KitCatalog.Kit kit = catalog.kits().get(kitId);
            if (kit == null) {
                throw new IllegalStateException("Unknown KIT '" + kitSetting + "'. Valid kits: "
                        + String.join(", ", catalog.kits().keySet()) + " (or leave KIT unset for every feature)");
            }
            preset = catalog.expand(kit);
        }

        Set<String> enabled = new LinkedHashSet<>(preset);
        String[] tokens = featuresSetting == null ? new String[0] : featuresSetting.trim().split("[,\\s]+");
        boolean explicit = false;
        for (String token : tokens) {
            if (!token.isEmpty() && !token.startsWith("+") && !token.startsWith("-")) {
                explicit = true;
            }
        }
        if (explicit) {
            enabled.clear();
        }
        for (String raw : tokens) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            boolean remove = token.startsWith("-");
            String id = (token.startsWith("+") || remove) ? token.substring(1).trim() : token;
            id = id.toLowerCase();
            if (KitCatalog.ALL.equals(id)) {
                if (remove) {
                    enabled.clear();
                } else {
                    enabled.addAll(catalog.features().keySet());
                }
                continue;
            }
            if (!catalog.features().containsKey(id)) {
                throw new IllegalStateException("Unknown feature '" + id + "' in FEATURES. Valid features: "
                        + String.join(", ", catalog.features().keySet()));
            }
            if (remove && catalog.features().get(id).alwaysOn()) {
                throw new IllegalStateException("Feature '" + id + "' is always on and cannot be removed (FEATURES="
                        + featuresSetting + ").");
            }
            if (remove) {
                enabled.remove(id);
            } else {
                enabled.add(id);
            }
        }
        catalog.features().values().stream().filter(KitCatalog.Feature::alwaysOn)
                .forEach(f -> enabled.add(f.id()));

        List<String> ordered = catalog.features().keySet().stream().filter(enabled::contains).toList();
        return new Resolution(kitId, ordered, !ordered.equals(preset));
    }
}
