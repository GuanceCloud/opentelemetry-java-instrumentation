/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaServiceTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void exportsStateEvent() throws Exception {
    CapturingExporter exporter = new CapturingExporter(MetaExportResult.SUCCESS);
    MetaService service =
        new MetaService(exporter, 10, 1000, "runtime-id", singletonMap("service.name", "checkout"));
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("value", "test"))).isTrue();
    assertThat(exporter.exported.await(5, SECONDS)).isTrue();

    MetaExportRequest request = exporter.request.get();
    assertThat(request.getRuntimeId()).isEqualTo("runtime-id");
    assertThat(request.getSequenceId()).isEqualTo(1);
    assertThat(request.getResource()).containsEntry("service.name", "checkout");
    assertThat(request.getEvents()).hasSize(1);
    assertThat(service.getFailedExports()).isZero();
  }

  @Test
  void rejectsUnknownEventType() {
    MetaService service =
        new MetaService(
            request -> MetaExportResult.SUCCESS,
            10,
            1000,
            "runtime-id",
            singletonMap("service.name", "checkout"));
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("custom-event", singletonMap("value", "test"))).isFalse();
  }

  @Test
  void countsFailedExport() throws Exception {
    CapturingExporter exporter = new CapturingExporter(MetaExportResult.PERMANENT_FAILURE);
    MetaService service =
        new MetaService(exporter, 10, 1000, "runtime-id", singletonMap("key", "value"));
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("value", "test"))).isTrue();
    assertThat(exporter.exported.await(5, SECONDS)).isTrue();
    service.close();

    assertThat(service.getFailedExports()).isEqualTo(1);
  }

  @Test
  void retriesFailedAppStartedOnNextHeartbeat() throws Exception {
    FailingOnceExporter exporter = new FailingOnceExporter();
    MetaService service =
        new MetaService(
            exporter,
            5,
            1000,
            50,
            10_000,
            "runtime-id",
            singletonMap("service.name", "checkout"),
            Collections::emptyMap);
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("startup_status", "success"))).isTrue();
    MetaExportRequest failed = exporter.requests.poll(5, SECONDS);
    MetaExportRequest retried = exporter.requests.poll(5, SECONDS);

    assertThat(failed).isNotNull();
    assertThat(failed.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-started");
    assertThat(retried).isNotNull();
    assertThat(retried.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-heartbeat", "app-started");
    assertThat(service.getFailedExports()).isEqualTo(1);
  }

  @Test
  void doesNotRetryPermanentlyFailedStateOnNextHeartbeat() throws Exception {
    FailingOnceExporter exporter = new FailingOnceExporter(MetaExportResult.PERMANENT_FAILURE);
    MetaService service =
        new MetaService(
            exporter,
            5,
            1000,
            50,
            10_000,
            "runtime-id",
            singletonMap("service.name", "checkout"),
            Collections::emptyMap);
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("startup_status", "success"))).isTrue();
    MetaExportRequest failed = exporter.requests.poll(5, SECONDS);
    MetaExportRequest heartbeat = exporter.requests.poll(5, SECONDS);

    assertThat(failed).isNotNull();
    assertThat(failed.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-started");
    assertThat(heartbeat).isNotNull();
    assertThat(heartbeat.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-heartbeat");
    assertThat(service.getFailedExports()).isEqualTo(1);
  }

  @Test
  void batchesEventsThatArriveWithinScheduleDelay() throws Exception {
    CapturingExporter exporter = new CapturingExporter(MetaExportResult.SUCCESS);
    MetaService service =
        new MetaService(exporter, 200, 1000, "runtime-id", singletonMap("key", "value"));
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("value", 1))).isTrue();
    assertThat(service.emit("app-dependencies-loaded", singletonMap("value", 2))).isTrue();
    assertThat(exporter.exported.await(5, SECONDS)).isTrue();

    assertThat(exporter.request.get().getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-started", "app-dependencies-loaded");
  }

  @Test
  void retainsOnlyTheLatestFullSnapshotOfEachTypeInABatch() throws Exception {
    CapturingExporter exporter = new CapturingExporter(MetaExportResult.SUCCESS);
    MetaService service =
        new MetaService(exporter, 200, 1000, "runtime-id", singletonMap("key", "value"));
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("value", 1))).isTrue();
    assertThat(service.emit("app-dependencies-loaded", singletonMap("value", 2))).isTrue();
    assertThat(service.emit("app-integrations-change", singletonMap("value", 3))).isTrue();
    assertThat(service.emit("app-dependencies-loaded", singletonMap("value", 4))).isTrue();
    assertThat(service.emit("app-integrations-change", singletonMap("value", 5))).isTrue();
    assertThat(exporter.exported.await(5, SECONDS)).isTrue();

    assertThat(exporter.request.get().getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-started", "app-dependencies-loaded", "app-integrations-change");
    assertThat(exporter.request.get().getEvents().get(1).getPayload()).containsEntry("value", 4);
    assertThat(exporter.request.get().getEvents().get(2).getPayload()).containsEntry("value", 5);
  }

  @Test
  void assignsSequenceToEachExportRequest() throws Exception {
    QueueingExporter exporter = new QueueingExporter();
    MetaService service =
        new MetaService(exporter, 10, 1000, "runtime-id", singletonMap("key", "value"));
    cleanup.deferCleanup(service);
    service.start();

    assertThat(service.emit("app-started", singletonMap("value", 1))).isTrue();
    MetaExportRequest first = exporter.requests.poll(5, SECONDS);
    assertThat(first).isNotNull();

    assertThat(service.emit("app-dependencies-loaded", singletonMap("value", 2))).isTrue();
    MetaExportRequest second = exporter.requests.poll(5, SECONDS);
    assertThat(second).isNotNull();

    assertThat(first.getSequenceId()).isEqualTo(1);
    assertThat(second.getSequenceId()).isEqualTo(2);
  }

  @Test
  void emitsLightweightHeartbeatAtConfiguredInterval() throws Exception {
    QueueingExporter exporter = new QueueingExporter();
    MetaService service =
        new MetaService(
            exporter,
            5,
            1000,
            20,
            10_000,
            "runtime-id",
            singletonMap("service.name", "checkout"),
            Collections::emptyMap);
    cleanup.deferCleanup(service);
    service.start();

    MetaExportRequest request = exporter.requests.poll(5, SECONDS);

    assertThat(request).isNotNull();
    assertThat(request.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-heartbeat");
    assertThat(request.getEvents().get(0).getPayload()).isEmpty();
    assertThat(request.getResource()).containsEntry("service.name", "checkout");
  }

  @Test
  void emitsExtendedHeartbeatWithFullCurrentSnapshots() throws Exception {
    QueueingExporter exporter = new QueueingExporter();
    Map<String, Object> snapshot =
        singletonMap("dependencies", singletonList(singletonMap("name", "demo")));
    MetaService service =
        new MetaService(
            exporter,
            5,
            1000,
            10_000,
            20,
            "runtime-id",
            singletonMap("service.name", "checkout"),
            () -> snapshot);
    cleanup.deferCleanup(service);
    service.start();

    MetaExportRequest request = exporter.requests.poll(5, SECONDS);

    assertThat(request).isNotNull();
    assertThat(request.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-extended-heartbeat");
    assertThat(request.getEvents().get(0).getPayload()).isEqualTo(snapshot);
  }

  @Test
  void retriesFailedExtendedHeartbeatOnNextOrdinaryHeartbeat() throws Exception {
    FailingOnceExporter exporter = new FailingOnceExporter();
    MetaService service =
        new MetaService(
            exporter,
            5,
            1000,
            800,
            500,
            "runtime-id",
            singletonMap("service.name", "checkout"),
            () -> singletonMap("dependencies", emptyList()));
    cleanup.deferCleanup(service);
    service.start();

    MetaExportRequest failed = exporter.requests.poll(5, SECONDS);
    MetaExportRequest retried = exporter.requests.poll(5, SECONDS);

    assertThat(failed).isNotNull();
    assertThat(failed.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-extended-heartbeat");
    assertThat(retried).isNotNull();
    assertThat(retried.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-heartbeat", "app-extended-heartbeat");
    assertThat(service.getFailedExports()).isEqualTo(1);
  }

  @Test
  void exportsLatestStateObservedDuringInFlightRequest() throws Exception {
    BlockingFirstExporter exporter = new BlockingFirstExporter();
    MetaService service =
        new MetaService(exporter, 5, 1000, "runtime-id", singletonMap("key", "value"));
    cleanup.deferCleanup(service);
    service.start();

    try {
      assertThat(service.emit("app-started", singletonMap("value", 1))).isTrue();
      assertThat(exporter.firstExportStarted.await(5, SECONDS)).isTrue();
      assertThat(service.emit("app-dependencies-loaded", singletonMap("value", 2))).isTrue();
      assertThat(service.emit("app-dependencies-loaded", singletonMap("value", 3))).isTrue();
    } finally {
      exporter.releaseFirstExport.countDown();
    }

    MetaExportRequest first = exporter.requests.poll(5, SECONDS);
    MetaExportRequest second = exporter.requests.poll(5, SECONDS);
    assertThat(first.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-started");
    assertThat(second.getEvents())
        .extracting(MetaEvent::getRequestType)
        .containsExactly("app-dependencies-loaded");
    assertThat(second.getEvents().get(0).getPayload()).containsEntry("value", 3);
  }

  @Test
  void shutdownTimeoutOnlyBoundsCallerWait() throws Exception {
    UninterruptibleExporter exporter = new UninterruptibleExporter();
    MetaService service =
        new MetaService(exporter, 5, 25, "runtime-id", singletonMap("key", "value"));
    service.start();
    assertThat(service.emit("app-started", singletonMap("value", 1))).isTrue();
    assertThat(exporter.exportStarted.await(5, SECONDS)).isTrue();

    long startedAt = System.nanoTime();
    try {
      service.close();
      assertThat(System.nanoTime() - startedAt).isLessThan(SECONDS.toNanos(1));
      assertThat(exporter.exportFinished.getCount()).isEqualTo(1);
    } finally {
      exporter.releaseExport.countDown();
    }
    assertThat(exporter.exportFinished.await(5, SECONDS)).isTrue();
  }

  private static final class CapturingExporter implements MetaExporter {
    private final MetaExportResult result;
    private final CountDownLatch exported = new CountDownLatch(1);
    private final AtomicReference<MetaExportRequest> request = new AtomicReference<>();

    private CapturingExporter(MetaExportResult result) {
      this.result = result;
    }

    @Override
    public MetaExportResult export(MetaExportRequest request) {
      this.request.set(request);
      exported.countDown();
      return result;
    }
  }

  private static final class QueueingExporter implements MetaExporter {
    private final BlockingQueue<MetaExportRequest> requests = new LinkedBlockingQueue<>();

    @Override
    public MetaExportResult export(MetaExportRequest request) {
      requests.add(request);
      return MetaExportResult.SUCCESS;
    }
  }

  private static final class FailingOnceExporter implements MetaExporter {
    private final BlockingQueue<MetaExportRequest> requests = new LinkedBlockingQueue<>();
    private final AtomicInteger attempts = new AtomicInteger();
    private final MetaExportResult firstResult;

    private FailingOnceExporter() {
      this(MetaExportResult.RETRYABLE_FAILURE);
    }

    private FailingOnceExporter(MetaExportResult firstResult) {
      this.firstResult = firstResult;
    }

    @Override
    public MetaExportResult export(MetaExportRequest request) {
      requests.add(request);
      return attempts.getAndIncrement() == 0 ? firstResult : MetaExportResult.SUCCESS;
    }
  }

  private static final class BlockingFirstExporter implements MetaExporter {
    private final BlockingQueue<MetaExportRequest> requests = new LinkedBlockingQueue<>();
    private final CountDownLatch firstExportStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstExport = new CountDownLatch(1);
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    @SuppressWarnings("Interruption") // The test exporter restores interruption before returning.
    public MetaExportResult export(MetaExportRequest request) {
      requests.add(request);
      if (attempts.getAndIncrement() == 0) {
        firstExportStarted.countDown();
        try {
          releaseFirstExport.await();
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
          return MetaExportResult.RETRYABLE_FAILURE;
        }
      }
      return MetaExportResult.SUCCESS;
    }
  }

  private static final class UninterruptibleExporter implements MetaExporter {
    private final CountDownLatch exportStarted = new CountDownLatch(1);
    private final CountDownLatch releaseExport = new CountDownLatch(1);
    private final CountDownLatch exportFinished = new CountDownLatch(1);

    @Override
    @SuppressWarnings("Interruption") // Deliberately simulates an I/O call that ignores interrupts.
    public MetaExportResult export(MetaExportRequest request) {
      exportStarted.countDown();
      boolean released = false;
      while (!released) {
        try {
          releaseExport.await();
          released = true;
        } catch (InterruptedException ignored) {
          // Keep waiting to verify that MetaService.close() still returns after its configured
          // caller wait timeout.
        }
      }
      exportFinished.countDown();
      return MetaExportResult.SUCCESS;
    }
  }
}
