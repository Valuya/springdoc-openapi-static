package io.github.valuya.springdoc.staticgen;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Makes the specification fit to commit: the same on every run, and free of the address it happened to be served
 * from.
 */
final class OpenApiSpecificationNormalizer {

    private static final String SERVERS_FIELD = "servers";

    private OpenApiSpecificationNormalizer() {
    }

    /**
     * @param sortKeys springdoc's schema property maps are hash-backed, so without sorting the specification comes out in
     *                 a different order on every JVM run
     */
    static String normalize(String specification, boolean removeServers, boolean sortKeys) {
        JsonMapper.Builder jsonMapperBuilder = JsonMapper.builder();
        if (sortKeys) {
            jsonMapperBuilder.enable(JsonNodeFeature.WRITE_PROPERTIES_SORTED);
        }
        ObjectMapper objectMapper = jsonMapperBuilder.build();
        JsonNode rootNode = objectMapper.readTree(specification);
        if (removeServers && rootNode instanceof ObjectNode rootObjectNode) {
            rootObjectNode.remove(SERVERS_FIELD);
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    }
}
