/**
 * Generates the OpenAPI specification springdoc would serve, without starting the application: only the controllers,
 * their advice and the configuration that shapes the specification are created, and every other dependency they
 * declare is a proxy that is never called.
 */
@NullMarked
package io.github.valuya.springdoc.staticgen;

import org.jspecify.annotations.NullMarked;
