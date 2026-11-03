package io.github.valuya.springdoc.staticgen.sample;

import org.jspecify.annotations.Nullable;

public record GreetingDto(String text, @Nullable String footnote) {
}
