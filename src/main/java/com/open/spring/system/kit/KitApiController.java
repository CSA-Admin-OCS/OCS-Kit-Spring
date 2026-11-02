package com.open.spring.system.kit;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * /api/kit/admin (ROLE_ADMIN only): feature sources, setup guides, readiness and kit presets, plus
 * applying a whole kit and per-feature on/off overrides at runtime. Only admins may change
 * features (canonical feature_management).
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

    /** Body: {"kit": "vanilla|education|business"}. Clears all overrides; accounts and data stay. */
    @PutMapping("/api/kit/admin/kit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> applyKit(@RequestBody Map<String, Object> body, Principal principal) {
        Object kit = body == null ? null : body.get("kit");
        features.applyKit(kit instanceof String id ? id : null, principal.getName());
        return features.describeAdmin();
    }

    /** Forgets the applied kit and all overrides, returning to the server's KIT/FEATURES. */
    @DeleteMapping("/api/kit/admin/kit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> forgetKit(Principal principal) {
        features.forgetKit(principal.getName());
        return features.describeAdmin();
    }

    /** Handled here because the global advice answers 500 for its JSON cause. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "The request body must be valid JSON"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
