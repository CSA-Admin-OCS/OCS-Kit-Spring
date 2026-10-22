package com.open.spring.system.kit;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KitConfig {

    /** Runs before Spring Security's filter chain (DEFAULT_FILTER_ORDER = -100). */
    @Bean
    public FilterRegistrationBean<FeatureGateFilter> featureGateFilter(KitFeatures features) {
        FilterRegistrationBean<FeatureGateFilter> registration = new FilterRegistrationBean<>(new FeatureGateFilter(features));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        registration.addUrlPatterns("/*");
        registration.setName("featureGateFilter");
        return registration;
    }
}
