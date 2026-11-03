package com.example.greeting;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GreetingOpenApiConfiguration {

    @Bean
    OpenApiCustomizer greetingTitleCustomizer() {
        return openApi -> openApi.getInfo().setTitle("Greetings");
    }
}
