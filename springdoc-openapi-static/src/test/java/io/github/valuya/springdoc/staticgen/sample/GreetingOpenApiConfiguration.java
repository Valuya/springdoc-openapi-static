package io.github.valuya.springdoc.staticgen.sample;

import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GreetingOpenApiConfiguration {

    static final String TITLE = "Greetings";

    @Bean
    public OpenApiCustomizer greetingTitleCustomizer() {
        return openApi -> {
            Info info = new Info().title(TITLE);
            openApi.info(info);
        };
    }
}
