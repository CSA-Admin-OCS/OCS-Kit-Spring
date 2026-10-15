package com.open.spring.system;

import java.io.File;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Kit startup checks, run after application.properties and .env are loaded.
 *
 * 1. JWT secret: required. Startup fails with a clear message when JWT_SECRET is
 *    unset, unless KIT_DEV_MODE=true, in which case a random per-boot secret is used.
 * 2. Boot from empty: when the SQLite database file does not exist yet and DDL_AUTO
 *    was not set explicitly, Hibernate runs with ddl-auto=update so a fresh
 *    container creates its schema. ModelInit then seeds it.
 */
public class KitEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "kitOverrides";
    private static final int MIN_JWT_SECRET_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();
        boolean devMode = Boolean.parseBoolean(environment.getProperty("kit.dev-mode", "false").trim());

        checkJwtSecret(environment, devMode, overrides);
        chooseDdlAuto(environment, overrides);

        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(SOURCE_NAME)) {
            sources.remove(SOURCE_NAME);
        }
        if (!overrides.isEmpty()) {
            sources.addFirst(new MapPropertySource(SOURCE_NAME, overrides));
        }
    }

    private void checkJwtSecret(ConfigurableEnvironment environment, boolean devMode, Map<String, Object> overrides) {
        String secret = environment.getProperty("jwt.secret", "").trim();
        if (secret.isEmpty()) {
            if (devMode) {
                byte[] random = new byte[48];
                new SecureRandom().nextBytes(random);
                overrides.put("jwt.secret", Base64.getEncoder().encodeToString(random));
                System.err.println("WARNING: JWT_SECRET is not set; KIT_DEV_MODE=true so a random secret "
                        + "is used for this run. Logins will not survive a restart.");
                return;
            }
            throw new IllegalStateException("JWT_SECRET is not set. Set it in .env or the environment "
                    + "(generate one with: openssl rand -base64 48), or set KIT_DEV_MODE=true for local development.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be base64 encoded (generate one with: openssl rand -base64 48).");
        }
        if (decoded.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least " + MIN_JWT_SECRET_BYTES
                    + " bytes (generate one with: openssl rand -base64 48).");
        }
    }

    private void chooseDdlAuto(ConfigurableEnvironment environment, Map<String, Object> overrides) {
        String explicit = environment.getProperty("DDL_AUTO", "").trim();
        String current = environment.getProperty("spring.jpa.hibernate.ddl-auto", "").trim();
        if (!explicit.isEmpty() || (!current.isEmpty() && !"none".equalsIgnoreCase(current))) {
            return; // operator (or a migration tool passing --spring.jpa.hibernate.ddl-auto) decided
        }
        if (current.isEmpty()) {
            overrides.put("spring.jpa.hibernate.ddl-auto", "none"); // "DDL_AUTO=" in .env means default
        }
        String url = environment.getProperty("spring.datasource.url", "").trim();
        if (!url.startsWith("jdbc:sqlite:")) {
            return; // remote databases: set DDL_AUTO=update for the first boot
        }
        String path = url.substring("jdbc:sqlite:".length());
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.isEmpty() || path.startsWith(":memory:")) {
            return;
        }
        File dbFile = new File(path);
        if (!dbFile.exists()) {
            File parent = dbFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            overrides.put("spring.jpa.hibernate.ddl-auto", "update");
            System.out.println("No database at " + dbFile.getPath() + "; creating schema (ddl-auto=update).");
        }
    }

    @Override
    public int getOrder() {
        // After ConfigDataEnvironmentPostProcessor, so application.properties and .env are visible.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
