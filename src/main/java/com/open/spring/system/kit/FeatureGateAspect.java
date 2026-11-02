package com.open.spring.system.kit;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

/**
 * Skips @Scheduled jobs and @EventListener methods of disabled features. The owning feature
 * comes from the declaring class's package (kits/catalog.json spring.packages), so new jobs
 * are gated without any per-feature code. Every run asks again, so jobs stop and resume with the
 * effective features. ApplicationReadyEvent listeners only get one chance, so a disabled one is
 * deferred until its feature is enabled, and one that fails is logged rather than stopping the site.
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
        Class<?> type = joinPoint.getSignature().getDeclaringType();
        String feature = features.featureFor(type);
        if (feature != null && !KitCatalog.CORE.equals(feature) && isStartupListener(joinPoint)) {
            features.runWhenEnabled(feature,
                    type.getSimpleName() + "." + joinPoint.getSignature().getName(), () -> proceedLater(joinPoint));
            return null;
        }
        return gate(joinPoint, "event listener");
    }

    private boolean isStartupListener(ProceedingJoinPoint joinPoint) {
        EventListener listener = AnnotatedElementUtils.findMergedAnnotation(
                ((MethodSignature) joinPoint.getSignature()).getMethod(), EventListener.class);
        return listener != null && List.of(listener.classes()).contains(ApplicationReadyEvent.class);
    }

    private void proceedLater(ProceedingJoinPoint joinPoint) throws Exception {
        try {
            joinPoint.proceed();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
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
