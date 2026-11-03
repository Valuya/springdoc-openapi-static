package io.github.valuya.springdoc.staticgen;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.context.servlet.AnnotationConfigServletWebApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Fills the generation context with what springdoc reads: the controllers and their advice found in the base
 * packages, and the classes the caller includes (an {@code OpenApiCustomizer}'s configuration). Nothing else of the
 * application is scanned.
 */
class StaticGenerationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROPERTY_SOURCE_NAME = "springdoc-static";

    private final List<String> basePackages;
    private final List<Class<?>> includedClasses;
    private final Map<String, Object> overridingProperties;

    StaticGenerationContextInitializer(List<String> basePackages, List<Class<?>> includedClasses, Map<String, Object> overridingProperties) {
        this.basePackages = basePackages;
        this.includedClasses = includedClasses;
        this.overridingProperties = overridingProperties;
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        AnnotationConfigServletWebApplicationContext webApplicationContext = (AnnotationConfigServletWebApplicationContext) applicationContext;
        MockServletContext servletContext = new MockServletContext();
        webApplicationContext.setServletContext(servletContext);

        DefaultListableBeanFactory beanFactory = webApplicationContext.getDefaultListableBeanFactory();
        UnavailableDependencyAutowireCandidateResolver autowireCandidateResolver = new UnavailableDependencyAutowireCandidateResolver();
        beanFactory.setAutowireCandidateResolver(autowireCandidateResolver);

        ConfigurableEnvironment environment = webApplicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        MapPropertySource overridingPropertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, overridingProperties);
        propertySources.addFirst(overridingPropertySource);

        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(webApplicationContext, false, environment);
        AnnotationTypeFilter controllerFilter = new AnnotationTypeFilter(Controller.class);
        AnnotationTypeFilter controllerAdviceFilter = new AnnotationTypeFilter(ControllerAdvice.class);
        scanner.addIncludeFilter(controllerFilter);
        scanner.addIncludeFilter(controllerAdviceFilter);
        String[] basePackageArray = basePackages.toArray(String[]::new);
        scanner.scan(basePackageArray);

        if (!includedClasses.isEmpty()) {
            Class<?>[] includedClassArray = includedClasses.toArray(Class<?>[]::new);
            webApplicationContext.register(includedClassArray);
        }
    }
}
