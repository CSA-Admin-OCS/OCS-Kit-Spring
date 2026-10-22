package com.open.spring.system.kit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Skips @Scheduled jobs and @EventListener methods of disabled features. The owning feature
 * comes from the declaring class's package (kits/catalog.json spring.packages), so new jobs
 * are gated without any per-feature code.
 */
@Aspect
@Component
public class FeatureGateAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureGateAspect.class);

    private final KitFeatures features;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public FeatureGateAspect(KitFeatures features) {
        this.features = features;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) && within(com.open.spring..*) && !within(com.open.spring.system..*)")
    public Object scheduled(ProceedingJoinPoint joinPoint) throws Throwable {
        return gate(joinPoint, "scheduled job");
    }

    @Around("@annotation(org.springframework.context.event.EventListener) && within(com.open.spring..*) && !within(com.open.spring.system..*)")
    public Object eventListener(ProceedingJoinPoint joinPoint) throws Throwable {
        return gate(joinPoint, "event listener");
    }

    private Object gate(ProceedingJoinPoint joinPoint, String kind) throws Throwable {
        Class<?> type = joinPoint.getSignature().getDeclaringType();
        if (features.isEnabledFor(type)) {
            return joinPoint.proceed();
        }
        String name = type.getSimpleName() + "." + joinPoint.getSignature().getName();
        if (reported.add(name)) {
            log.info("Skipping {} {}: feature '{}' is disabled in this kit", kind, name, features.featureFor(type));
        }
        return null;
    }
}
