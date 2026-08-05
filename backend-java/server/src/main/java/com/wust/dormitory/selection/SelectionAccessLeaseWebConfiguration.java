package com.wust.dormitory.selection;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SelectionAccessLeaseWebConfiguration implements WebMvcConfigurer {
    private final SelectionAccessLeaseInterceptor interceptor;

    public SelectionAccessLeaseWebConfiguration(SelectionAccessLeaseInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/student/batches/**")
                .order(30);
    }
}
