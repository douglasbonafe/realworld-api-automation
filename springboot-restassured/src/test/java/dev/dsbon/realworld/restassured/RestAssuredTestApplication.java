package dev.dsbon.realworld.restassured;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The Spring context the tests boot.
 *
 * <p>Unlike the RestClient module, this class lives under {@code src/test} —
 * there is no client library to publish here, because REST Assured <i>is</i> the
 * client. Spring's only job is configuration and lifecycle: binding {@code
 * realworld.base-url}, and building the shared request and response
 * specifications once for the whole run.
 *
 * <p><b>Its package placement is load-bearing.</b> {@code @SpringBootApplication}
 * scans its own package and everything below it, so this class sits at {@code
 * dev.dsbon.realworld.restassured} — the parent of {@code .spec}, {@code
 * .support} and {@code .tests}. Putting it inside {@code .support} (a sibling of
 * {@code .spec}) would leave the {@link dev.dsbon.realworld.restassured.spec.Specs}
 * configuration unscanned, and every test would fail on a missing
 * {@code RequestSpecification} bean.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RestAssuredTestApplication {
  // No main method: nothing should ever run this as a service.
}
