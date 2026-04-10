/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_PARAMETER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractJdbcInstrumentationAutoConfigurationTest {

  protected abstract InstrumentationExtension testing();

  protected abstract AutoConfigurations autoConfigurations();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(autoConfigurations())
          .withBean("openTelemetry", OpenTelemetry.class, testing()::getOpenTelemetry);

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void querySanitizerEnabledByDefault() {
    contextRunner.run(
        context -> {
          DataSource dataSource = context.getBean(DataSource.class);

          assertThat(AopUtils.isAopProxy(dataSource)).isTrue();
          assertThat(dataSource.getClass().getSimpleName()).isNotEqualTo("HikariDataSource");
          // unwrap the instrumented data source to get the original data source
          Object original = ((Advised) dataSource).getTargetSource().getTarget();
          assertThat(AopUtils.isAopProxy(original)).isFalse();
          assertThat(original.getClass().getSimpleName()).isEqualTo("HikariDataSource");

          try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
              statement.execute("SELECT 1");
            }
          }

          testing()
              .waitAndAssertTraces(
                  trace ->
                      trace.hasSpansSatisfyingExactly(
                          span -> span.hasAttribute(maybeStable(DB_STATEMENT), "SELECT ?")));
        });
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void captureQueryParametersEnabledByCompatibilityProperty() {
    contextRunner
        .withPropertyValues("otel.jdbc.sql.obfuscation=true")
        .run(
            context -> {
              DataSource dataSource = context.getBean(DataSource.class);

              try (Connection connection = dataSource.getConnection();
                  PreparedStatement statement = connection.prepareStatement("SELECT ?")) {
                statement.setInt(1, 1);
                statement.execute();
              }

              testing()
                  .waitAndAssertTraces(
                      trace ->
                          trace.hasSpansSatisfyingExactly(
                              span ->
                                  span.hasAttribute(maybeStable(DB_STATEMENT), "SELECT ?")
                                      .hasAttribute(
                                          DB_QUERY_PARAMETER.getAttributeKey("0"), "1")));
            });
  }
}
