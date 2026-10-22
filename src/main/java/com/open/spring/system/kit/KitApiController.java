package com.open.spring.system.kit;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/kit (public): kit, enabled features, role labels, so frontends can hide what is off.
 * /api/kit/admin (ROLE_ADMIN only): feature sources, setup guides and readiness, and runtime
 * on/off overrides. Only admins may change features (canonical feature_management).
 */
@RestController
public class KitApiController {

    private final KitFeatures features;

    public KitApiController(KitFeatures features) {
        this.features = features;
    }

    @GetMapping("/api/kit")
    public Map<String, Object> kit() {
        return features.describePublic();
    }

    @GetMapping("/api/kit/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> admin() {
        return features.describeAdmin();
    }

    /** Body: {"features": {"finance-sim": true, "hallpass": false}}. */
    @PutMapping("/api/kit/admin/features")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> update(@RequestBody Map<String, Map<String, Boolean>> body, Principal principal) {
        features.applyOverrides(body == null ? null : body.get("features"), principal.getName());
        return features.describeAdmin();
    }

    @DeleteMapping("/api/kit/admin/features/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> clear(@PathVariable String id, Principal principal) {
        features.clearOverride(id, principal.getName());
        return features.describeAdmin();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
