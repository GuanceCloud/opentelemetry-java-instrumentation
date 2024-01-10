/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbSetArgs {
  private Map<Integer, String> args;

  public DbSetArgs(Map<Integer, String> map) {
    this.args = map;
  }

  public Map<Integer, String> getArgs() {
    return this.args;
  }

  public void setArgs(Map<Integer, String> args) {
    this.args = args;
  }

  public void setArg(Integer index, String arg) {
    this.args.put(index, arg);
  }

  public void resetArgs() {
    this.args = new HashMap<Integer, String>();
  }
}
