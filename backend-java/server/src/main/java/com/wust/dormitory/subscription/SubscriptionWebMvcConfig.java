package com.wust.dormitory.subscription;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SubscriptionWebMvcConfig implements WebMvcConfigurer {
    private final FeatureAccessInterceptor featureAccessInterceptor;

    public SubscriptionWebMvcConfig(FeatureAccessInterceptor featureAccessInterceptor) {
        this.featureAccessInterceptor = featureAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(featureAccessInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
