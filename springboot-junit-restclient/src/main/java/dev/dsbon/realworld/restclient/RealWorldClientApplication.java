package dev.dsbon.realworld.restclient;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The Spring Boot application the tests boot.
 *
 * <p>It serves nothing — {@code @SpringBootTest} runs it with the {@code NONE}
 * web environment. Its only job is to be a context: somewhere for the {@code
 * RestClient} bean, the typed API facade and the bound configuration properties
 * to live so tests can have them injected instead of constructing them.
 *
 * <p>That is the actual argument for this module over the plain-Java one. The
 * client stops being test scaffolding and becomes a component — configurable
 * through {@code application.yml}, overridable per test class, and reusable by
 * production code that needs to talk to the same API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RealWorldClientApplication {
  // No main method on purpose: nothing should ever run this as a service.
}
