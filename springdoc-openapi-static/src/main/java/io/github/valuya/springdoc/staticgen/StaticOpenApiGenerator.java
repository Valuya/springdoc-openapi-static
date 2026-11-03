package io.github.valuya.springdoc.staticgen;

import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.servlet.AnnotationConfigServletWebApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the specification springdoc serves at {@code /v3/api-docs}, from a Spring context that holds the
 * controllers, their advice, springdoc and Spring MVC, and nothing that needs a database, a server or a network.
 * <p>
 * The specification is springdoc's own answer, read through {@link MockMvc}: the same code path as a running
 * application's, with the application's configuration files, profiles and {@code OpenApiCustomizer}s.
 */
public final class StaticOpenApiGenerator {

    /**
     * What springdoc needs to read controllers: property binding, Jackson, Spring MVC and Spring Data's web support (the
     * {@code Pageable} parameters). Everything else Spring Boot would configure is left out.
     */
    public static final List<String> DEFAULT_AUTO_CONFIGURATION_PREFIXES = List.of(
        "org.springdoc.",
        "org.springframework.boot.autoconfigure.context.",
        "org.springframework.boot.jackson.autoconfigure.",
        "org.springframework.boot.http.converter.autoconfigure.",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration",
        "org.springframework.boot.validation.autoconfigure.",
        "org.springframework.boot.data.autoconfigure.web."
    );

    private static final String CONTEXT_PATH_PROPERTY = "server.servlet.context-path";
    private static final String API_DOCS_PATH_PROPERTY = "springdoc.api-docs.path";
    private static final String DEFAULT_API_DOCS_PATH = "/v3/api-docs";

    private final List<String> basePackages;
    private final List<Class<?>> includedClasses;
    private final List<String> profiles;
    private final Map<String, Object> properties;
    private final List<String> autoConfigurationPrefixes;
    private final boolean removeServers;
    private final boolean sortKeys;

    private StaticOpenApiGenerator(Builder builder) {
        this.basePackages = List.copyOf(builder.basePackages);
        this.includedClasses = List.copyOf(builder.includedClasses);
        this.profiles = List.copyOf(builder.profiles);
        this.properties = Map.copyOf(builder.properties);
        this.autoConfigurationPrefixes = List.copyOf(builder.autoConfigurationPrefixes);
        this.removeServers = builder.removeServers;
        this.sortKeys = builder.sortKeys;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String generate() {
        Map<String, Object> overridingProperties = new LinkedHashMap<>(properties);
        String autoConfigurationPrefixList = String.join(",", autoConfigurationPrefixes);
        overridingProperties.put(StaticAutoConfigurationImportSelector.AUTO_CONFIGURATIONS_PROPERTY, autoConfigurationPrefixList);
        StaticGenerationContextInitializer contextInitializer = new StaticGenerationContextInitializer(basePackages, includedClasses, overridingProperties);

        SpringApplication springApplication = new SpringApplication(StaticGenerationConfiguration.class);
        springApplication.setWebApplicationType(WebApplicationType.SERVLET);
        ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.of(AnnotationConfigServletWebApplicationContext::new);
        springApplication.setApplicationContextFactory(applicationContextFactory);
        springApplication.setBannerMode(Banner.Mode.OFF);
        springApplication.setRegisterShutdownHook(false);
        springApplication.setLogStartupInfo(false);
        String[] profileArray = profiles.toArray(String[]::new);
        springApplication.setAdditionalProfiles(profileArray);
        springApplication.addInitializers(contextInitializer);

        try (ConfigurableApplicationContext applicationContext = springApplication.run()) {
            String specification = readSpecification((WebApplicationContext) applicationContext, applicationContext.getEnvironment());
            return OpenApiSpecificationNormalizer.normalize(specification, removeServers, sortKeys);
        }
    }

    public void generate(Path outputFile) {
        String specification = generate();
        try {
            Path outputDirectory = outputFile.toAbsolutePath().getParent();
            Files.createDirectories(outputDirectory);
            Files.writeString(outputFile, specification, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write the OpenAPI specification to " + outputFile, exception);
        }
    }

    private static String readSpecification(WebApplicationContext webApplicationContext, ConfigurableEnvironment environment) {
        String contextPath = environment.getProperty(CONTEXT_PATH_PROPERTY, "");
        String apiDocsPath = environment.getProperty(API_DOCS_PATH_PROPERTY, DEFAULT_API_DOCS_PATH);
        String apiDocsUrl = contextPath + apiDocsPath;
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get(apiDocsUrl)
            .contextPath(contextPath);
        try {
            MvcResult mvcResult = mockMvc.perform(requestBuilder).andReturn();
            MockHttpServletResponse response = mvcResult.getResponse();
            int status = response.getStatus();
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            if (status != 200) {
                throw new IllegalStateException("springdoc answered %s with %d: %s".formatted(apiDocsUrl, status, body));
            }
            return body;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read the OpenAPI specification from " + apiDocsUrl, exception);
        }
    }

    public static final class Builder {

        private final List<String> basePackages = new ArrayList<>();
        private final List<Class<?>> includedClasses = new ArrayList<>();
        private final List<String> profiles = new ArrayList<>();
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final List<String> autoConfigurationPrefixes = new ArrayList<>(DEFAULT_AUTO_CONFIGURATION_PREFIXES);
        private boolean removeServers = true;
        private boolean sortKeys = true;

        private Builder() {
        }

        /** Where the controllers and their {@code @ControllerAdvice} are scanned for. */
        public Builder basePackages(String... basePackages) {
            this.basePackages.addAll(List.of(basePackages));
            return this;
        }

        /** Configuration that shapes the specification and is not a controller: an {@code OpenApiCustomizer}'s, an {@code OpenAPI} bean's. */
        public Builder includedClasses(Class<?>... includedClasses) {
            this.includedClasses.addAll(List.of(includedClasses));
            return this;
        }

        public Builder profiles(String... profiles) {
            this.profiles.addAll(List.of(profiles));
            return this;
        }

        /** Overrides the application's configuration files. */
        public Builder property(String name, Object value) {
            this.properties.put(name, value);
            return this;
        }

        /** Lets more of Spring Boot's auto-configuration in, by class name prefix. */
        public Builder autoConfigurationPrefixes(String... autoConfigurationPrefixes) {
            this.autoConfigurationPrefixes.addAll(List.of(autoConfigurationPrefixes));
            return this;
        }

        /** Whether to drop the {@code servers} springdoc derives from the request; the default. */
        public Builder removeServers(boolean removeServers) {
            this.removeServers = removeServers;
            return this;
        }

        /** Whether to sort every object's keys, so the output is the same on every run; the default. */
        public Builder sortKeys(boolean sortKeys) {
            this.sortKeys = sortKeys;
            return this;
        }

        public StaticOpenApiGenerator build() {
            if (basePackages.isEmpty()) {
                throw new IllegalStateException("No base package to scan for controllers");
            }
            return new StaticOpenApiGenerator(this);
        }
    }
}
