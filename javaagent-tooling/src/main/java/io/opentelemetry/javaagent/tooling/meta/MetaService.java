/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Level.WARNING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class MetaService implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(MetaService.class.getName());
  private static final int MAX_EVENTS_PER_REQUEST = 5;

  private final MetaExporter exporter;
  // State is retained in pendingStateEvents. This single-slot queue only wakes the worker.
  private final BlockingQueue<Boolean> wakeup = new ArrayBlockingQueue<>(1);
  private final long scheduleDelayMillis;
  private final long shutdownTimeoutMillis;
  private final long heartbeatIntervalNanos;
  private final long extendedHeartbeatIntervalNanos;
  private final Supplier<Map<String, Object>> extendedHeartbeatPayloadSupplier;
  private final String runtimeId;
  private final Map<String, Object> resource;
  private final AtomicLong sequenceId = new AtomicLong();
  private final AtomicLong failedExports = new AtomicLong();
  private final AtomicBoolean running = new AtomicBoolean();
  private final Thread workerThread;

  // Guarded by this. Stateful events retain only their latest value until a successful export.
  private final Map<String, MetaEvent> pendingStateEvents = new LinkedHashMap<>();
  private final Map<String, MetaEvent> deferredStateEvents = new LinkedHashMap<>();

  // Accessed only by workerThread.
  private long nextHeartbeatNanos = Long.MAX_VALUE;
  private long nextExtendedHeartbeatNanos = Long.MAX_VALUE;
  private boolean extendedHeartbeatPending;
  private boolean extendedHeartbeatReady;

  MetaService(
      MetaExporter exporter, MetaConfiguration configuration, Map<String, Object> resource) {
    this(
        exporter,
        configuration.getScheduleDelayMillis(),
        configuration.getShutdownTimeoutMillis(),
        configuration.getHeartbeatIntervalMillis(),
        configuration.getExtendedHeartbeatIntervalMillis(),
        UUID.randomUUID().toString(),
        resource,
        MetaTelemetry::extendedHeartbeatPayload);
  }

  MetaService(
      MetaExporter exporter,
      long scheduleDelayMillis,
      long shutdownTimeoutMillis,
      String runtimeId,
      Map<String, Object> resource) {
    this(
        exporter,
        scheduleDelayMillis,
        shutdownTimeoutMillis,
        0,
        0,
        runtimeId,
        resource,
        Collections::emptyMap);
  }

  MetaService(
      MetaExporter exporter,
      long scheduleDelayMillis,
      long shutdownTimeoutMillis,
      long heartbeatIntervalMillis,
      long extendedHeartbeatIntervalMillis,
      String runtimeId,
      Map<String, Object> resource,
      Supplier<Map<String, Object>> extendedHeartbeatPayloadSupplier) {
    this.exporter = exporter;
    this.scheduleDelayMillis = scheduleDelayMillis;
    this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    this.heartbeatIntervalNanos = MILLISECONDS.toNanos(heartbeatIntervalMillis);
    this.extendedHeartbeatIntervalNanos = MILLISECONDS.toNanos(extendedHeartbeatIntervalMillis);
    this.runtimeId = runtimeId;
    this.resource = resource;
    this.extendedHeartbeatPayloadSupplier = extendedHeartbeatPayloadSupplier;
    this.workerThread = new Thread(this::runWorker, "opentelemetry-meta-exporter");
    this.workerThread.setDaemon(true);
    this.workerThread.setContextClassLoader(null);
  }

  void start() {
    if (running.compareAndSet(false, true)) {
      workerThread.start();
    }
  }

  synchronized boolean emit(String requestType, Map<String, Object> payload) {
    if (!running.get()) {
      return false;
    }
    MetaEvent event = new MetaEvent(System.currentTimeMillis() / 1000, requestType, payload);
    if (!isStateful(requestType)) {
      return false;
    }
    pendingStateEvents.put(requestType, event);
    deferredStateEvents.remove(requestType);
    signalStateChange();
    return true;
  }

  long getFailedExports() {
    return failedExports.get();
  }

  String getRuntimeId() {
    return runtimeId;
  }

  private void runWorker() {
    try {
      long startedAt = System.nanoTime();
      if (heartbeatIntervalNanos > 0) {
        nextHeartbeatNanos = nextDeadline(startedAt, heartbeatIntervalNanos);
      }
      if (extendedHeartbeatIntervalNanos > 0) {
        nextExtendedHeartbeatNanos = nextDeadline(startedAt, extendedHeartbeatIntervalNanos);
      }

      while (running.get() || hasReadyStateEvents()) {
        List<MetaEvent> batch = new ArrayList<>(MAX_EVENTS_PER_REQUEST);
        appendScheduledEvents(batch, System.nanoTime());
        boolean stateReadyWithoutSignal = hasReadyStateEvents() && wakeup.isEmpty();
        if (batch.isEmpty() && !stateReadyWithoutSignal) {
          Boolean stateChanged;
          try {
            long waitNanos = nanosUntilScheduledEvent(System.nanoTime());
            stateChanged =
                waitNanos == Long.MAX_VALUE ? wakeup.take() : wakeup.poll(waitNanos, NANOSECONDS);
          } catch (InterruptedException ignored) {
            if (running.get()) {
              continue;
            }
            stateChanged = wakeup.poll();
          }
          if (stateChanged == null) {
            continue;
          }
          collectBatch();
        }

        appendScheduledEvents(batch, System.nanoTime());
        appendReadyStateEvents(batch);
        if (batch.isEmpty()) {
          continue;
        }

        boolean containsExtendedHeartbeat = containsEvent(batch, "app-extended-heartbeat");
        MetaExportResult result = MetaExportResult.RETRYABLE_FAILURE;
        try {
          result =
              exporter.export(
                  new MetaExportRequest(
                      sequenceId.incrementAndGet(),
                      System.currentTimeMillis() / 1000,
                      runtimeId,
                      resource,
                      batch));
        } catch (RuntimeException e) {
          logger.log(WARNING, "Meta exporter failed unexpectedly", e);
        }
        if (result == MetaExportResult.SUCCESS) {
          acknowledgeStateEvents(batch);
          if (containsExtendedHeartbeat) {
            extendedHeartbeatPending = false;
          }
        } else {
          failedExports.incrementAndGet();
          if (result == MetaExportResult.RETRYABLE_FAILURE) {
            deferStateEvents(batch);
          } else {
            acknowledgeStateEvents(batch);
            if (containsExtendedHeartbeat) {
              extendedHeartbeatPending = false;
            }
          }
        }
      }
    } catch (Throwable t) {
      logger.log(WARNING, "Meta exporter worker stopped unexpectedly", t);
    } finally {
      exporter.shutdown();
    }
  }

  private void collectBatch() {
    long batchDeadline = nextDeadline(System.nanoTime(), MILLISECONDS.toNanos(scheduleDelayMillis));
    while (running.get()) {
      long now = System.nanoTime();
      long remainingNanos = Math.min(batchDeadline - now, nanosUntilScheduledEvent(now));
      if (remainingNanos <= 0) {
        break;
      }
      Boolean stateChanged;
      try {
        stateChanged = wakeup.poll(remainingNanos, NANOSECONDS);
      } catch (InterruptedException ignored) {
        if (running.get()) {
          continue;
        }
        break;
      }
      if (stateChanged == null) {
        break;
      }
    }
  }

  private long nanosUntilScheduledEvent(long now) {
    if (!running.get()) {
      return Long.MAX_VALUE;
    }
    if (extendedHeartbeatReady) {
      return 0;
    }
    long nextDeadline = Math.min(nextHeartbeatNanos, nextExtendedHeartbeatNanos);
    if (nextDeadline == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    }
    long remaining = nextDeadline - now;
    return remaining <= 0 ? 0 : remaining;
  }

  private void appendScheduledEvents(List<MetaEvent> batch, long now) {
    if (!running.get()) {
      return;
    }

    if (nextExtendedHeartbeatNanos <= now) {
      nextExtendedHeartbeatNanos = nextDeadline(now, extendedHeartbeatIntervalNanos);
      extendedHeartbeatPending = true;
      extendedHeartbeatReady = true;
    }

    if (nextHeartbeatNanos <= now && batch.size() < MAX_EVENTS_PER_REQUEST) {
      batch.add(newEvent("app-heartbeat", emptyMap()));
      nextHeartbeatNanos = nextDeadline(now, heartbeatIntervalNanos);
      retryDeferredStateEvents();
      if (extendedHeartbeatPending) {
        extendedHeartbeatReady = true;
      }
    }

    if (extendedHeartbeatReady && batch.size() < MAX_EVENTS_PER_REQUEST) {
      try {
        batch.add(newEvent("app-extended-heartbeat", extendedHeartbeatPayloadSupplier.get()));
      } catch (RuntimeException e) {
        logger.log(WARNING, "Unable to create the Meta extended heartbeat payload", e);
      }
      extendedHeartbeatReady = false;
    }
  }

  private static MetaEvent newEvent(String requestType, Map<String, Object> payload) {
    return new MetaEvent(System.currentTimeMillis() / 1000, requestType, payload);
  }

  private static boolean containsEvent(List<MetaEvent> events, String requestType) {
    for (MetaEvent event : events) {
      if (event.getRequestType().equals(requestType)) {
        return true;
      }
    }
    return false;
  }

  private void signalStateChange() {
    wakeup.offer(Boolean.TRUE);
  }

  private synchronized boolean hasReadyStateEvents() {
    for (Map.Entry<String, MetaEvent> entry : pendingStateEvents.entrySet()) {
      if (deferredStateEvents.get(entry.getKey()) != entry.getValue()) {
        return true;
      }
    }
    return false;
  }

  private synchronized void appendReadyStateEvents(List<MetaEvent> batch) {
    for (Map.Entry<String, MetaEvent> entry : pendingStateEvents.entrySet()) {
      if (batch.size() >= MAX_EVENTS_PER_REQUEST) {
        return;
      }
      if (deferredStateEvents.get(entry.getKey()) != entry.getValue()) {
        batch.add(entry.getValue());
      }
    }
  }

  private synchronized void acknowledgeStateEvents(List<MetaEvent> events) {
    for (MetaEvent event : events) {
      String requestType = event.getRequestType();
      if (pendingStateEvents.get(requestType) == event) {
        pendingStateEvents.remove(requestType);
        deferredStateEvents.remove(requestType);
      }
    }
  }

  private synchronized void deferStateEvents(List<MetaEvent> events) {
    for (MetaEvent event : events) {
      String requestType = event.getRequestType();
      if (pendingStateEvents.get(requestType) == event) {
        deferredStateEvents.put(requestType, event);
      }
    }
  }

  private synchronized void retryDeferredStateEvents() {
    deferredStateEvents.clear();
  }

  private static boolean isStateful(String requestType) {
    return requestType.equals("app-started")
        || requestType.equals("app-dependencies-loaded")
        || requestType.equals("app-integrations-change");
  }

  private static long nextDeadline(long now, long interval) {
    return now > Long.MAX_VALUE - interval ? Long.MAX_VALUE : now + interval;
  }

  @Override
  @SuppressWarnings("Interruption") // Wakes this service's owned worker so shutdown can flush now.
  public void close() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    retryDeferredStateEvents();
    signalStateChange();
    if (Thread.currentThread() == workerThread) {
      return;
    }
    workerThread.interrupt();
    try {
      workerThread.join(shutdownTimeoutMillis);
      if (workerThread.isAlive()) {
        logger.log(
            WARNING,
            "Timed out after {0} ms waiting for the Meta exporter worker to stop; an in-flight export may continue on its daemon thread",
            shutdownTimeoutMillis);
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }
}
