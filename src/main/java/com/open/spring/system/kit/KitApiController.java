package com.open.spring.system.kit;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: which kit and features this backend runs, so frontends can hide what is off. */
@RestController
public class KitApiController {

    private final KitFeatures features;

    public KitApiController(KitFeatures features) {
        this.features = features;
    }

    @GetMapping("/api/kit")
    public Map<String, Object> kit() {
        return features.describe();
    }
}
