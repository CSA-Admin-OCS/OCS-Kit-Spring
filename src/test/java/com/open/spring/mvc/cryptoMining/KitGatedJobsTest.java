package com.open.spring.mvc.cryptoMining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.open.spring.system.kit.FeatureGateAspect;
import com.open.spring.system.kit.FeatureOverrideStore;
import com.open.spring.system.kit.KitCatalog;
import com.open.spring.system.kit.KitFeatures;

/** Jobs of a feature (this package belongs to finance-sim) stop and resume with kit switches. */
class KitGatedJobsTest {

    static class Jobs {
        int scheduled;
        int ready;

        @Scheduled(fixedRate = 1000)
        public void job() {
            scheduled++;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            ready++;
        }
    }

    private final Jobs target = new Jobs();

    private Jobs gated(KitFeatures features) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new FeatureGateAspect(features));
        factory.setProxyTargetClass(true);
        return factory.getProxy();
    }

    private KitFeatures vanilla() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", new FeatureOverrideStore.InMemory());
        return new KitFeatures(KitCatalog.load(), "vanilla", "", key -> null,
                beans.getBeanProvider(FeatureOverrideStore.class), event -> { });
    }

    @Test
    void scheduledJobsStopAndResumeWithTheEffectiveFeatures() {
        KitFeatures features = vanilla();
        Jobs jobs = gated(features);

        jobs.job();
        assertEquals(0, target.scheduled);

        features.applyOverrides(java.util.Map.of("finance-sim", true), "admin");
        jobs.job();
        assertEquals(1, target.scheduled);

        features.applyKit("education", "admin"); // clears the override
        jobs.job();
        assertEquals(1, target.scheduled);

        features.applyOverrides(java.util.Map.of("finance-sim", true), "admin");
        jobs.job();
        assertEquals(2, target.scheduled);

        features.forgetKit("admin");
        jobs.job();
        assertEquals(2, target.scheduled);
    }

    @Test
    void aReadyListenerOfADisabledFeatureRunsOnceWhenItIsEnabled() {
        KitFeatures features = vanilla();
        Jobs jobs = gated(features);

        jobs.onReady();
        assertEquals(0, target.ready);

        features.applyOverrides(java.util.Map.of("finance-sim", true), "admin");
        assertEquals(1, target.ready);

        features.applyKit("vanilla", "admin");
        features.applyOverrides(java.util.Map.of("finance-sim", true), "admin");
        assertEquals(1, target.ready);
    }
}
