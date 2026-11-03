package io.github.valuya.springdoc.staticgen;

import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;

/**
 * Keeps Spring Boot's ordering and conditions of the auto-configurations it lets through, by being Spring Boot's own
 * selector with a shorter candidate list.
 */
class StaticAutoConfigurationImportSelector extends AutoConfigurationImportSelector {

    static final String AUTO_CONFIGURATIONS_PROPERTY = "springdoc-static.auto-configurations";

    @Override
    protected Class<?> getAnnotationClass() {
        return EnableStaticAutoConfiguration.class;
    }

    @Override
    protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
        List<String> candidateConfigurations = super.getCandidateConfigurations(metadata, attributes);
        Environment environment = getEnvironment();
        Binder binder = Binder.get(environment);
        List<String> allowedPrefixes = binder.bind(AUTO_CONFIGURATIONS_PROPERTY, Bindable.listOf(String.class))
            .orElse(List.of());
        return candidateConfigurations.stream()
            .filter(candidateConfiguration -> allowedPrefixes.stream().anyMatch(candidateConfiguration::startsWith))
            .toList();
    }
}
