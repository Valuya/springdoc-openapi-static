package io.github.valuya.springdoc.staticgen;

import io.github.valuya.springdoc.staticgen.sample.GreetingController;
import io.github.valuya.springdoc.staticgen.sample.GreetingOpenApiConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StaticOpenApiGeneratorTest {

    private static JsonNode specificationNode;

    @BeforeAll
    static void generateSpecification() {
        String samplePackage = GreetingController.class.getPackageName();
        StaticOpenApiGenerator staticOpenApiGenerator = StaticOpenApiGenerator.builder()
            .basePackages(samplePackage)
            .includedClasses(GreetingOpenApiConfiguration.class)
            .build();
        String specification = staticOpenApiGenerator.generate();
        JsonMapper jsonMapper = JsonMapper.builder().build();
        specificationNode = jsonMapper.readTree(specification);
    }

    @Test
    void documentsTheOperationOfAControllerWhoseServiceIsNoBean() {
        JsonNode responseSchemaNode = specificationNode.at("/paths/~1greetings~1{name}/get/responses/200/content/*~1*/schema/$ref");
        assertThat(responseSchemaNode.asString()).isEqualTo("#/components/schemas/GreetingDto");
    }

    @Test
    void documentsTheSchemaOfTheResponse() {
        JsonNode textTypeNode = specificationNode.at("/components/schemas/GreetingDto/properties/text/type");
        assertThat(textTypeNode.asString()).isEqualTo("string");
    }

    @Test
    void documentsTheStatusOfTheControllerAdvice() {
        JsonNode conflictNode = specificationNode.at("/paths/~1greetings~1salutation/get/responses/409");
        assertThat(conflictNode.isMissingNode()).isFalse();
    }

    @Test
    void appliesTheIncludedCustomizer() {
        JsonNode titleNode = specificationNode.at("/info/title");
        assertThat(titleNode.asString()).isEqualTo("Greetings");
    }

    @Test
    void removesTheServersDerivedFromTheRequest() {
        assertThat(specificationNode.has("servers")).isFalse();
    }
}
