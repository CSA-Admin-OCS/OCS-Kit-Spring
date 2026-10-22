package com.open.spring.system.kit;

/** Published when an admin turns a feature on at runtime, so its seed data can be created. */
public record FeatureEnabledEvent(String featureId) {
}
