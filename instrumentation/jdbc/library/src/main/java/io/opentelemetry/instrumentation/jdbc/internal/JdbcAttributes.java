/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("UnusedTypeParameter")
public final class JdbcAttributes<REQUEST, RESPONSE> implements AttributesExtractor<REQUEST, RESPONSE> {

  private  DbSetArgs args;

  private JdbcAttributes(){}

  public static JdbcAttributes<DbRequest, Void> create(DbSetArgs args) {
    JdbcAttributes<DbRequest, Void> j = new JdbcAttributes<>();
    j.setArgs(args);
    return j;
  }

  private void setArgs(DbSetArgs args) {
    this.args = args;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    if (this.args == null || this.args.getArgs()==null){
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Integer,String> entry : args.getArgs().entrySet()) {
      // internalSet(attributes, AttributeKey.stringKey("origin_sql_"+entry.getKey()),entry.getValue());
      sb.append("index_").
          append(entry.getKey()).
          append(":").
          append(entry.getValue()).
          append(", ");
    }
    internalSet(attributes, AttributeKey.stringKey("db_args"),sb.toString());
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context, REQUEST request,
      @Nullable RESPONSE response, @Nullable Throwable error) {
  }
}
