package io.github.valuya.springdoc.staticgen;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.Map;

/**
 * Puts the generator's properties above the application's configuration files. It runs once those are loaded and
 * before the logging system reads its levels, which an initializer would be too late for.
 */
class OverridingPropertySourceListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "springdoc-static";
    /** After {@code EnvironmentPostProcessorApplicationListener}, before {@code LoggingApplicationListener} */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 15;

    private final Map<String, Object> overridingProperties;

    OverridingPropertySourceListener(Map<String, Object> overridingProperties) {
        this.overridingProperties = overridingProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        MapPropertySource overridingPropertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, overridingProperties);
        propertySources.addFirst(overridingPropertySource);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
