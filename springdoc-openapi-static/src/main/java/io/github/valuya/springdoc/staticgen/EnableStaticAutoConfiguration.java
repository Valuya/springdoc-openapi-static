package io.github.valuya.springdoc.staticgen;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring Boot's auto-configuration, narrowed to the classes whose name starts with one of
 * {@value StaticAutoConfigurationImportSelector#AUTO_CONFIGURATIONS_PROPERTY}: a database, a security filter chain or a
 * message broker has nothing to say about the specification and would only need something to connect to.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(StaticAutoConfigurationImportSelector.class)
public @interface EnableStaticAutoConfiguration {

    Class<?>[] exclude() default {};

    String[] excludeName() default {};
}
