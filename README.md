# springdoc-openapi-static

Writes the OpenAPI specification [springdoc](https://springdoc.org) serves at `/v3/api-docs` at build time, from your
compiled controllers. No database, no running server, no tests to run.

It is not part of springdoc. It is a separate project that drives springdoc's own code.

## Why

The usual ways to commit a spec both boot the whole application:

- `springdoc-openapi-maven-plugin` starts the app and calls its endpoint.
- A `@SpringBootTest` that writes the spec needs whatever the app needs at startup: a database, a broker, credentials.
  It is also skipped with `-DskipTests`.

This project starts a Spring context that holds only:

- your `@Controller`s and `@ControllerAdvice`s;
- the configuration classes you name, such as an `OpenApiCustomizer`'s;
- springdoc, Spring MVC, Jackson, validation and Spring Data's web support (for `Pageable`).

If a controller needs a service or repository that is not a bean, it gets a lazy proxy instead. springdoc never calls
it. The spec is then read from springdoc's endpoint through `MockMvc`, so it comes from the same code path as a running
application, with your `application.yaml`, profiles and customizers applied.

The output is normalized so it can be committed: `servers` is removed and keys are sorted, so a rebuild with no API
change produces no diff.

## Maven plugin

```xml
<plugin>
  <groupId>io.github.valuya</groupId>
  <artifactId>springdoc-openapi-static-maven-plugin</artifactId>
  <version>0.1.1</version>
  <configuration>
    <basePackages>
      <basePackage>com.example.api</basePackage>
    </basePackages>
    <includedClasses>
      <includedClass>com.example.api.config.OpenApiConfig</includedClass>
    </includedClasses>
    <outputFile>${project.basedir}/docs/openapi.json</outputFile>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

`generate` runs in `process-classes` by default. It forks a JVM on the project's runtime classpath, so the spec comes
from your own springdoc and Spring Boot versions and none of their classes load into Maven.

The fork also needs `spring-test` (for `MockMvc`). If the runtime classpath doesn't have it, the plugin resolves it at
the version of the project's `spring-core`.

| Parameter | Default | |
|---|---|---|
| `basePackages` | required | Where controllers and their advice are scanned for |
| `includedClasses` | none | Configuration classes to register as well: customizers, an `OpenAPI` bean |
| `outputFile` | `${project.build.directory}/openapi.json` | |
| `profiles` | none | Extra profiles to activate |
| `properties` | none | Overrides your configuration files |
| `autoConfigurationPrefixes` | none | Lets more of Spring Boot's auto-configuration in, by class name prefix |
| `removeServers` | `true` | |
| `sortKeys` | `true` | |
| `jvmArguments` | none | For the forked JVM |
| `javaExecutable` | the JVM running Maven | |
| `skip` | `false` | `-Dspringdoc-openapi-static.skip` |

### Fail CI when the committed spec is stale

The plugin only writes the file. To fail a build when the committed copy is out of date, compare the two after the
build:

```bash
mvn -B install -DskipTests
git diff --exit-code docs/openapi.json
```

## Library

Use the generator directly from a test, a script or another build tool. Spring Boot, springdoc and `spring-test` are
`provided`, so they come from your application:

```java
StaticOpenApiGenerator.builder()
    .basePackages("com.example.api")
    .includedClasses(OpenApiConfig.class)
    .build()
    .generate(Path.of("docs", "openapi.json"));
```

## Supported versions

| | |
|---|---|
| Java | 17+ |
| Spring Boot | 4.0, 4.1 |
| springdoc | 3.0, 3.1, 3.2 (`springdoc-openapi-starter-webmvc-api` or `-ui`) |
| Web stack | Spring MVC only. WebFlux is not supported |

Spring Boot 3 and springdoc 2 would need a separate build on Jackson 2.

## Limits

- **Dependencies that can't be proxied fail startup.** This applies to a required dependency, with no bean to satisfy
  it, whose type is `final`, a `java.*` type, a collection or a map. Make it a bean in an included configuration class,
  or depend on an interface instead.
- **Code that runs while the context starts still runs.** That covers `@PostConstruct` methods, constructor bodies and
  `ApplicationListener`s on controllers or included classes. If that code calls a proxied service, the call fails.
- **Only the auto-configuration listed in `StaticOpenApiGenerator.DEFAULT_AUTO_CONFIGURATION_PREFIXES` is applied.**
  Actuator endpoints, Spring Security's filter chain and Spring Data REST are left out unless you add their prefixes.

## License

[Apache License 2.0](LICENSE)
