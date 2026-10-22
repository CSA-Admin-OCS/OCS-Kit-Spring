package com.open.spring.system.kit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Wraps CommandLineRunner/ApplicationRunner beans that belong to an optional feature (usually
 * sample-data seeders such as cryptoMining.DataInitializer). At startup the runner runs only if
 * its feature is enabled; otherwise it is deferred and runs when an admin enables the feature.
 * The owner is the class declaring the runner, or the @Configuration class whose @Bean method
 * returned the lambda. Classes annotated {@link KitAlwaysRun} (schema migrations) are left alone.
 */
@Component
public class FeatureGateRunnerPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<KitFeatures> features;

    public FeatureGateRunnerPostProcessor(ObjectProvider<KitFeatures> features) {
        this.features = features;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof CommandLineRunner) && !(bean instanceof ApplicationRunner)) {
            return bean;
        }
        Class<?> owner = ClassUtils.getUserClass(bean);
        if (owner.isSynthetic() || owner.isHidden()) {
            owner = owner.getNestHost(); // lambda returned by a @Bean method
        }
        if (owner.isAnnotationPresent(KitAlwaysRun.class)) {
            return bean;
        }
        KitFeatures kit = features.getObject();
        String feature = kit.featureFor(owner);
        if (feature == null || KitCatalog.CORE.equals(feature)) {
            return bean;
        }
        String name = beanName + " (" + owner.getSimpleName() + ")";
        if (bean instanceof ApplicationRunner runner) {
            return (ApplicationRunner) args -> features.getObject().runWhenEnabled(feature, name, () -> runner.run(args));
        }
        CommandLineRunner runner = (CommandLineRunner) bean;
        return (CommandLineRunner) args -> features.getObject().runWhenEnabled(feature, name, () -> runner.run(args));
    }
}
