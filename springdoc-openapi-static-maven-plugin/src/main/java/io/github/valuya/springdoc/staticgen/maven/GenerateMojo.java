package io.github.valuya.springdoc.staticgen.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the OpenAPI specification springdoc would serve, from the project's compiled controllers.
 * <p>
 * The generator runs in a JVM of its own, on the project's runtime classpath: the specification comes from the
 * application's own springdoc and Spring Boot, and none of their classes reach Maven's.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    private static final String GENERATOR_ARTIFACT_KEY = "io.github.valuya:springdoc-openapi-static";
    private static final String GENERATOR_MAIN_CLASS = "io.github.valuya.springdoc.staticgen.StaticOpenApiGeneratorMain";
    private static final String SPRING_GROUP_ID = "org.springframework";
    private static final String SPRING_CORE_ARTIFACT_ID = "spring-core";
    private static final String SPRING_TEST_ARTIFACT_ID = "spring-test";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor pluginDescriptor;

    /** Where to write the specification. */
    @Parameter(property = "springdoc-openapi-static.outputFile", defaultValue = "${project.build.directory}/openapi.json", required = true)
    private File outputFile;

    /** Where the controllers and their {@code @ControllerAdvice} are scanned for. */
    @Parameter(required = true)
    private List<String> basePackages;

    /** Configuration classes that shape the specification and are not controllers: an {@code OpenApiCustomizer}'s. */
    @Parameter
    private List<String> includedClasses = new ArrayList<>();

    @Parameter
    private List<String> profiles = new ArrayList<>();

    /** Overrides the application's configuration files. */
    @Parameter
    private Map<String, String> properties = Map.of();

    /** Lets more of Spring Boot's auto-configuration in, by class name prefix. */
    @Parameter
    private List<String> autoConfigurationPrefixes = new ArrayList<>();

    /** Whether to drop the {@code servers} springdoc derives from the request. */
    @Parameter(defaultValue = "true")
    private boolean removeServers;

    /** Whether to sort every object's keys, so the output is the same on every run. */
    @Parameter(defaultValue = "true")
    private boolean sortKeys;

    @Parameter
    private List<String> jvmArguments = new ArrayList<>();

    /** The {@code java} to fork; the one running Maven by default. */
    @Parameter
    private File javaExecutable;

    @Parameter(property = "springdoc-openapi-static.skip", defaultValue = "false")
    private boolean skip;

    private final RepositorySystem repositorySystem;

    @Inject
    public GenerateMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping the OpenAPI specification");
            return;
        }
        List<String> classpathElements = getForkClasspathElements();
        String classpath = String.join(File.pathSeparator, classpathElements);
        List<String> javaArguments = new ArrayList<>();
        javaArguments.add("-cp");
        javaArguments.add(classpath);
        javaArguments.addAll(jvmArguments);
        javaArguments.add(GENERATOR_MAIN_CLASS);
        List<String> generatorArguments = getGeneratorArguments();
        javaArguments.addAll(generatorArguments);

        Path argumentFile = writeArgumentFile(javaArguments);
        String java = getJavaExecutablePath();
        String argumentFileArgument = "@" + argumentFile;
        File projectDirectory = project.getBasedir();
        ProcessBuilder processBuilder = new ProcessBuilder(java, argumentFileArgument)
            .directory(projectDirectory)
            .inheritIO();
        getLog().info("Writing the OpenAPI specification to " + outputFile);
        int exitCode = runProcess(processBuilder);
        if (exitCode != 0) {
            throw new MojoFailureException("The OpenAPI specification generator exited with " + exitCode + ", see its output above");
        }
    }

    private List<String> getForkClasspathElements() throws MojoExecutionException {
        List<String> classpathElements = new ArrayList<>(getRuntimeClasspathElements());
        Artifact generatorArtifact = pluginDescriptor.getArtifactMap().get(GENERATOR_ARTIFACT_KEY);
        if (generatorArtifact == null) {
            throw new MojoExecutionException("The plugin's own " + GENERATOR_ARTIFACT_KEY + " dependency is missing");
        }
        String generatorPath = generatorArtifact.getFile().getAbsolutePath();
        classpathElements.add(generatorPath);
        Set<Artifact> projectArtifacts = project.getArtifacts();
        Optional<Artifact> springTestArtifactOptional = findSpringArtifactOptional(projectArtifacts, SPRING_TEST_ARTIFACT_ID);
        if (springTestArtifactOptional.isEmpty()) {
            File springTestFile = resolveSpringTest(projectArtifacts);
            String springTestPath = springTestFile.getAbsolutePath();
            classpathElements.add(springTestPath);
        }
        return classpathElements;
    }

    private List<String> getRuntimeClasspathElements() throws MojoExecutionException {
        try {
            return project.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException exception) {
            throw new MojoExecutionException("The project's runtime classpath is not resolved", exception);
        }
    }

    /** MockMvc and MockServletContext, at the version of the application's own Spring. */
    private File resolveSpringTest(Set<Artifact> projectArtifacts) throws MojoExecutionException {
        Artifact springCoreArtifact = findSpringArtifactOptional(projectArtifacts, SPRING_CORE_ARTIFACT_ID)
            .orElseThrow(() -> new MojoExecutionException("No " + SPRING_GROUP_ID + ":" + SPRING_CORE_ARTIFACT_ID
                + " on the project's runtime classpath: is this a Spring application?"));
        String springVersion = springCoreArtifact.getVersion();
        DefaultArtifact springTestArtifact = new DefaultArtifact(SPRING_GROUP_ID, SPRING_TEST_ARTIFACT_ID, "jar", springVersion);
        List<RemoteRepository> remoteRepositories = project.getRemoteProjectRepositories();
        ArtifactRequest artifactRequest = new ArtifactRequest(springTestArtifact, remoteRepositories, null);
        RepositorySystemSession repositorySession = session.getRepositorySession();
        try {
            ArtifactResult artifactResult = repositorySystem.resolveArtifact(repositorySession, artifactRequest);
            return artifactResult.getArtifact().getFile();
        } catch (ArtifactResolutionException exception) {
            throw new MojoExecutionException("Could not resolve " + springTestArtifact, exception);
        }
    }

    private static Optional<Artifact> findSpringArtifactOptional(Set<Artifact> artifacts, String artifactId) {
        return artifacts.stream()
            .filter(artifact -> SPRING_GROUP_ID.equals(artifact.getGroupId()))
            .filter(artifact -> artifactId.equals(artifact.getArtifactId()))
            .findFirst();
    }

    private List<String> getGeneratorArguments() {
        List<String> generatorArguments = new ArrayList<>();
        String outputPath = outputFile.getAbsolutePath();
        generatorArguments.add("--output=" + outputPath);
        basePackages.forEach(basePackage -> generatorArguments.add("--base-package=" + basePackage));
        includedClasses.forEach(includedClass -> generatorArguments.add("--included-class=" + includedClass));
        profiles.forEach(profile -> generatorArguments.add("--profile=" + profile));
        properties.forEach((name, value) -> generatorArguments.add("--property=" + name + "=" + value));
        autoConfigurationPrefixes.forEach(prefix -> generatorArguments.add("--auto-configuration-prefix=" + prefix));
        generatorArguments.add("--remove-servers=" + removeServers);
        generatorArguments.add("--sort-keys=" + sortKeys);
        return generatorArguments;
    }

    /** A classpath easily outgrows a command line, so every argument goes through a java {@code @argfile}. */
    private Path writeArgumentFile(List<String> javaArguments) throws MojoExecutionException {
        Path workDirectory = Path.of(project.getBuild().getDirectory(), "springdoc-openapi-static");
        Path argumentFile = workDirectory.resolve("java-arguments");
        StringBuilder argumentFileContent = new StringBuilder();
        for (String javaArgument : javaArguments) {
            String quotedArgument = quoteArgument(javaArgument);
            argumentFileContent.append(quotedArgument).append('\n');
        }
        try {
            Files.createDirectories(workDirectory);
            Files.writeString(argumentFile, argumentFileContent, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not write " + argumentFile, exception);
        }
        return argumentFile;
    }

    private static String quoteArgument(String argument) {
        String escapedArgument = argument
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
        return "\"" + escapedArgument + "\"";
    }

    private String getJavaExecutablePath() {
        if (javaExecutable != null) {
            return javaExecutable.getAbsolutePath();
        }
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path java = javaHome.resolve("bin").resolve("java");
        return java.toString();
    }

    private static int runProcess(ProcessBuilder processBuilder) throws MojoExecutionException {
        try {
            Process process = processBuilder.start();
            return process.waitFor();
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not start " + processBuilder.command(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while generating the OpenAPI specification", exception);
        }
    }
}
