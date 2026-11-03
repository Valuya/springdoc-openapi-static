package io.github.valuya.springdoc.staticgen;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Runs the generator in a JVM of its own, on the application's classpath: what the Maven plugin forks.
 * <p>
 * Every argument is {@code --name=value}; the repeatable ones can be given several times:
 * <pre>
 * --output=docs/openapi.json            required
 * --base-package=com.example.api        required, repeatable
 * --included-class=com.example.Config   repeatable
 * --profile=openapi                     repeatable
 * --property=springdoc.x=y              repeatable
 * --auto-configuration-prefix=org.x.    repeatable
 * --remove-servers=false                default true
 * --sort-keys=false                     default true
 * </pre>
 */
public final class StaticOpenApiGeneratorMain {

    private StaticOpenApiGeneratorMain() {
    }

    public static void main(String[] arguments) {
        StaticOpenApiGenerator.Builder builder = StaticOpenApiGenerator.builder();
        @Nullable Path outputFile = null;
        for (String argument : arguments) {
            int separatorIndex = argument.indexOf('=');
            if (!argument.startsWith("--") || separatorIndex < 0) {
                throw new IllegalArgumentException("Not a --name=value argument: " + argument);
            }
            String name = argument.substring(2, separatorIndex);
            String value = argument.substring(separatorIndex + 1);
            switch (name) {
                case "output" -> outputFile = Path.of(value);
                case "base-package" -> builder.basePackages(value);
                case "included-class" -> builder.includedClasses(loadClass(value));
                case "profile" -> builder.profiles(value);
                case "property" -> addProperty(builder, value);
                case "auto-configuration-prefix" -> builder.autoConfigurationPrefixes(value);
                case "remove-servers" -> builder.removeServers(Boolean.parseBoolean(value));
                case "sort-keys" -> builder.sortKeys(Boolean.parseBoolean(value));
                default -> throw new IllegalArgumentException("Unknown argument: --" + name);
            }
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("No --output file");
        }
        StaticOpenApiGenerator staticOpenApiGenerator = builder.build();
        staticOpenApiGenerator.generate(outputFile);
    }

    private static Class<?> loadClass(String className) {
        ClassLoader classLoader = StaticOpenApiGeneratorMain.class.getClassLoader();
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("No class " + className + " on the application's classpath", exception);
        }
    }

    private static void addProperty(StaticOpenApiGenerator.Builder builder, String nameAndValue) {
        int separatorIndex = nameAndValue.indexOf('=');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Not a name=value property: " + nameAndValue);
        }
        String propertyName = nameAndValue.substring(0, separatorIndex);
        String propertyValue = nameAndValue.substring(separatorIndex + 1);
        builder.property(propertyName, propertyValue);
    }
}
