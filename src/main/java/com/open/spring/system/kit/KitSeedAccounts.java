package com.open.spring.system.kit;

import java.util.ArrayList;
import java.util.List;

/**
 * The kit's seed accounts: one per canonical role (admin, staff, member) with the kit's default
 * uid and label and the Spring authorities from catalog spring.roles. Person.init() reads the
 * active set; ADMIN_* / TEACHER_* / USER_* environment settings still override uid, name, email.
 */
public final class KitSeedAccounts {

    public record Account(String role, String uid, String label, List<String> authorities) {
        /** Default display name, e.g. "Manager User". */
        public String defaultName() {
            return label + " User";
        }

        public String defaultEmail() {
            return uid + "@example.com";
        }
    }

    private static volatile List<Account> active;

    private KitSeedAccounts() { }

    public static List<Account> forKit(KitCatalog catalog, KitCatalog.Kit kit) {
        List<Account> accounts = new ArrayList<>();
        for (String role : List.of("admin", "staff", "member")) {
            KitCatalog.Role r = kit.roles().get(role);
            String uid = r == null || r.seedUid().isBlank() ? role : r.seedUid();
            String label = r == null ? role : r.label();
            accounts.add(new Account(role, uid, label, catalog.authoritiesFor(role)));
        }
        return List.copyOf(accounts);
    }

    /** Set by {@link KitFeatures} at startup. */
    static void activate(List<Account> accounts) {
        active = accounts;
    }

    /** Active seed accounts, or the education kit's (the platform's original roles) before startup. */
    public static List<Account> active() {
        List<Account> current = active;
        if (current == null) {
            KitCatalog catalog = KitCatalog.load();
            KitCatalog.Kit kit = catalog.kits().getOrDefault(KitFeatures.FALLBACK_ROLE_KIT, catalog.kits().values().iterator().next());
            current = forKit(catalog, kit);
            active = current;
        }
        return current;
    }

    public static Account get(String role) {
        return active().stream().filter(a -> a.role().equals(role)).findFirst().orElseThrow();
    }
}
