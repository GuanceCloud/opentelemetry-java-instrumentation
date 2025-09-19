/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createDataSourceInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcNetworkAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.DbSetArgs;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributes;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Collections;
import javax.sql.DataSource;

import java.util.HashMap;

public final class JdbcSingletons {
  private static final Instrumenter<DbRequest, Void> STATEMENT_INSTRUMENTER;
  private static final Instrumenter<DbRequest, Void> TRANSACTION_INSTRUMENTER;
  public static final Instrumenter<DataSource, DbInfo> DATASOURCE_INSTRUMENTER =
      createDataSourceInstrumenter(GlobalOpenTelemetry.get(), true);
  public static final boolean CAPTURE_QUERY_PARAMETERS;

  public static final DbSetArgs setArgs;

  public static void setArg(Integer index,String arg){
    if (setArgs == null){
      return;
    }
    setArgs.setArg(index,arg);
  }

  public static void resetArgs(){
    setArgs.resetArgs();
  }

  static {
    JdbcNetworkAttributesGetter netAttributesGetter = new JdbcNetworkAttributesGetter();
/*<<<<<<< HEAD
    setArgs = new DbSetArgs(new HashMap<>());

    STATEMENT_INSTRUMENTER =
        Instrumenter.<DbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(dbAttributesGetter)
                    .setStatementSanitizationEnabled(
                        AgentInstrumentationConfig.get()
                            .getBoolean(
                                "otel.instrumentation.jdbc.statement-sanitizer.enabled",
                                AgentCommonConfig.get().isStatementSanitizationEnabled()))
                    .build())
            .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    netAttributesGetter, AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get())
            .addAttributesExtractor(JdbcAttributes.create(setArgs))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
=======*/
    AttributesExtractor<DbRequest, Void> peerServiceExtractor =
        PeerServiceAttributesExtractor.create(
            netAttributesGetter, AgentCommonConfig.get().getPeerServiceResolver());

    CAPTURE_QUERY_PARAMETERS =
        AgentInstrumentationConfig.get()
            .getBoolean("otel.instrumentation.jdbc.experimental.capture-query-parameters", false);

    STATEMENT_INSTRUMENTER =
        JdbcInstrumenterFactory.createStatementInstrumenter(
            GlobalOpenTelemetry.get(),
            Collections.singletonList(peerServiceExtractor),
            true,
            AgentInstrumentationConfig.get()
                .getBoolean(
                    "otel.instrumentation.jdbc.statement-sanitizer.enabled",
                    AgentCommonConfig.get().isStatementSanitizationEnabled()),
            CAPTURE_QUERY_PARAMETERS);

    TRANSACTION_INSTRUMENTER =
        JdbcInstrumenterFactory.createTransactionInstrumenter(
            GlobalOpenTelemetry.get(),
            Collections.singletonList(peerServiceExtractor),
            AgentInstrumentationConfig.get()
                .getBoolean("otel.instrumentation.jdbc.experimental.transaction.enabled", false));
  }

  public static Instrumenter<DbRequest, Void> transactionInstrumenter() {
    return TRANSACTION_INSTRUMENTER;
//>>>>>>> v2200
  }

  public static Instrumenter<DbRequest, Void> statementInstrumenter() {
    return STATEMENT_INSTRUMENTER;
  }

  public static Instrumenter<DataSource, DbInfo> dataSourceInstrumenter() {
    return DATASOURCE_INSTRUMENTER;
  }

  private JdbcSingletons() {}
}
