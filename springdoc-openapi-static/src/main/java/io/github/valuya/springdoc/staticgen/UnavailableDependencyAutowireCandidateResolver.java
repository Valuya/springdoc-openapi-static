package io.github.valuya.springdoc.staticgen;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/**
 * Hands a controller a lazy proxy for each dependency no bean of the generation context satisfies: a service, a
 * repository, a client. springdoc reads the controller's methods and never calls it, so the proxy is never resolved;
 * a call would fail with Spring's own "no such bean" exception, naming the dependency.
 * <p>
 * A type no proxy can stand for (a final class, a JDK class, a value) is left to Spring's normal resolution, which
 * fails the generation with the dependency's name.
 */
class UnavailableDependencyAutowireCandidateResolver extends ContextAnnotationAutowireCandidateResolver {

    @Override
    public @Nullable Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
        Object lazyAnnotatedProxy = super.getLazyResolutionProxyIfNecessary(descriptor, beanName);
        if (lazyAnnotatedProxy != null) {
            return lazyAnnotatedProxy;
        }
        if (!descriptor.isRequired() || getSuggestedValue(descriptor) != null) {
            return null;
        }
        Class<?> dependencyType = descriptor.getDependencyType();
        if (!isProxyable(dependencyType) || hasCandidate(dependencyType)) {
            return null;
        }
        return buildLazyResolutionProxy(descriptor, beanName);
    }

    private boolean hasCandidate(Class<?> dependencyType) {
        BeanFactory beanFactory = getBeanFactory();
        if (!(beanFactory instanceof ListableBeanFactory listableBeanFactory)) {
            return true;
        }
        String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(listableBeanFactory, dependencyType, true, false);
        return candidateNames.length > 0;
    }

    private static boolean isProxyable(Class<?> dependencyType) {
        if (dependencyType.isInterface()) {
            return !Collection.class.isAssignableFrom(dependencyType) && !Map.class.isAssignableFrom(dependencyType);
        }
        int modifiers = dependencyType.getModifiers();
        String packageName = dependencyType.getPackageName();
        boolean jdkClass = packageName.startsWith("java.") || packageName.startsWith("javax.");
        return !dependencyType.isPrimitive()
            && !dependencyType.isArray()
            && !Modifier.isFinal(modifiers)
            && !jdkClass;
    }
}
