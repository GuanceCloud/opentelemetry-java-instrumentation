# Meta exporter

The Guance Java agent can export agent and application runtime metadata through a dedicated HTTP
pipeline. Meta data is independent of the OpenTelemetry trace, metric, and log exporters.

The exporter reports application startup, loaded dependencies, applied Java agent instrumentations,
and process heartbeats. These events share one process-level `runtime_id` and the same resource
metadata. Dependency and integration payloads are full current-state snapshots, not increments.

## Configuration

Meta export is disabled by default. Enable it by selecting the `meta` exporter and configuring an
explicit endpoint:

```properties
otel.meta.exporter=meta
otel.exporter.meta.endpoint=https://example.com/v1/meta
```

Available properties:

| System property                               | Environment variable                          | Default | Description                                  |
| --------------------------------------------- | --------------------------------------------- | ------- | -------------------------------------------- |
| `otel.meta.exporter`                          | `OTEL_META_EXPORTER`                          | `none`  | Meta exporter: `none` or `meta`.             |
| `otel.exporter.meta.endpoint`                 | `OTEL_EXPORTER_META_ENDPOINT`                 | none    | Complete HTTP or HTTPS destination URL.      |
| `otel.exporter.meta.headers`                  | `OTEL_EXPORTER_META_HEADERS`                  | none    | Comma-separated request headers.             |
| `otel.exporter.meta.timeout`                  | `OTEL_EXPORTER_META_TIMEOUT`                  | `10000` | Connect and read timeout in milliseconds.    |
| `otel.exporter.meta.compression`              | `OTEL_EXPORTER_META_COMPRESSION`              | `none`  | Request compression: `none` or `gzip`.       |
| `otel.exporter.meta.max-retries`              | `OTEL_EXPORTER_META_MAX_RETRIES`              | `3`     | Maximum retries after the initial request.   |
| `otel.exporter.meta.max-request-size`         | `OTEL_EXPORTER_META_MAX_REQUEST_SIZE`         | `5242880` | Maximum serialized request size in bytes.  |
| `otel.meta.batch.schedule-delay`              | `OTEL_META_BATCH_SCHEDULE_DELAY`              | `1000`  | Event batching window in milliseconds.       |
| `otel.meta.shutdown-timeout`                  | `OTEL_META_SHUTDOWN_TIMEOUT`                  | `10000` | Maximum time shutdown waits for the worker, in milliseconds. |
| `otel.meta.heartbeat.interval`                 | `OTEL_META_HEARTBEAT_INTERVAL`                 | `60000` | Lightweight heartbeat interval in milliseconds. |
| `otel.meta.extended-heartbeat.interval`        | `OTEL_META_EXTENDED_HEARTBEAT_INTERVAL`        | `86400000` | Full-state heartbeat interval in milliseconds. |
| `otel.meta.app-started.enabled`               | `OTEL_META_APP_STARTED_ENABLED`               | `true`  | Emit the `app-started` event.                |
| `otel.meta.app-dependencies-loaded.enabled`   | `OTEL_META_APP_DEPENDENCIES_LOADED_ENABLED`   | `true`  | Discover and emit loaded application JARs.   |
| `otel.meta.app-integrations-change.enabled`   | `OTEL_META_APP_INTEGRATIONS_CHANGE_ENABLED`   | `true`  | Emit successfully applied instrumentations. |

Meta never inherits the generic OTLP endpoint or the signal-specific trace, metric, and log
endpoints. If `otel.meta.exporter=meta` is selected without a valid endpoint, the agent disables
Meta export and continues starting the application.

`otel.meta.batch.export-timeout` remains accepted as a compatibility alias for
`otel.meta.shutdown-timeout`. The new property takes precedence when both are configured. The
timeout only bounds how long the shutdown hook waits; it cannot forcibly cancel a JVM HTTP call,
so an in-flight export may finish later on the daemon worker.

## Protocol

The exporter sends versioned JSON batches to the configured URL using HTTP `POST`:

```json
{
  "api_version": "v1",
  "runtime_id": "7c066414-4c5a-4ba3-bc21-0d175d2019a1",
  "seq_id": 1,
  "tracer_time": 1787713200,
  "resource": {
    "service.name": "checkout",
    "deployment.environment.name": "production",
    "service.version": "1.4.0",
    "telemetry.distro.version": "2.30.2",
    "telemetry.sdk.language": "java",
    "process.runtime.name": "OpenJDK Runtime Environment",
    "process.runtime.version": "17.0.12+7",
    "host.name": "checkout-6d9f8c7d8b-2xk9m",
    "host.arch": "amd64",
    "os.type": "linux",
    "os.name": "Linux",
    "os.description": "Ubuntu 24.04.2 LTS (Noble Numbat)",
    "os.version": "6.11.0-26-generic",
    "os.kernel.version": "#26~24.04.1-Ubuntu SMP PREEMPT_DYNAMIC"
  },
  "events": [
    {
      "timestamp": 1787713200,
      "request_type": "app-started",
      "payload": {
        "startup_status": "success"
      }
    }
  ]
}
```

`seq_id` starts at one for each `runtime_id` and increments once per HTTP request; retries reuse the
same request and sequence. `tracer_time` is the request creation time in Unix seconds. An event
`timestamp` is its observation time and has no independent sequence.

Application, runtime, and host identity are consolidated into the OpenTelemetry `resource` object.
Equivalent Datadog fields are not duplicated: for example, `service_name` maps to `service.name`,
`tracer_version` to `telemetry.distro.version`, `hostname` to `host.name`, and `architecture` to
`host.arch`. Values are sourced from the allowlisted OpenTelemetry resource where applicable and
otherwise from JVM system properties, `/etc/os-release`, and `/proc/version` with safe fallbacks.

Fields such as `class`, `name`, `create_time`, `last_update_time`, `date`, `date_ns`, and `time_us`
are storage/index fields in a Datadog-style backend record, not agent protocol fields. A receiver
can derive them from `runtime_id`, `tracer_time`, and `resource`. Event columns such as
`app_started` and `app_dependencies_loaded` should likewise be derived from the structured `events`
array instead of sending JSON strings inside JSON.

The endpoint treats any `2xx` response as success. The exporter retries network failures, `408`,
`429`, and `5xx` responses with bounded exponential backoff. It does not fall back to an OTLP
endpoint or follow redirects automatically. Other `4xx` responses, serialization failures, and
oversized requests are permanent failures and are not retried.

Request JSON is written through a size-limited stream. With gzip enabled, both the uncompressed
JSON and transmitted compressed body must fit `otel.exporter.meta.max-request-size`; the exporter
does not allocate an unbounded intermediate JSON byte array.

`app-started`, dependency, and integration events retain their latest value until a request is
acknowledged successfully or fails permanently. After a retryable failure, the current value is
retried with the next heartbeat, or sooner if a newer snapshot replaces it. A permanently failed
value is discarded so that it cannot poison later heartbeat batches; a newer snapshot remains
eligible for export. Only the latest dependency and integration snapshots are retained; stale full
snapshots are not accumulated in the event queue.

Each completed export writes one Java agent log record. Successful exports use `INFO`; final
failures after retries use `WARNING`. Records include the sanitized endpoint, HTTP status,
`seq_id`, event count, and attempt count. Request bodies, headers, URL user information, query
parameters, and fragments are never logged. Detailed exception diagnostics are limited to `FINE`.

The `/v1/meta` protocol is custom and is not an OTLP signal.

## Events

### `app-started`

Emitted once after the Meta service starts. Its payload contains the installation method, startup
status, and the enabled Meta capabilities. The agent version is reported as
`resource.telemetry.distro.version` rather than duplicated in the event payload.

### `app-dependencies-loaded`

The agent observes the code source of application classes as the JVM defines them. Each distinct
JAR location is resolved on a dedicated daemon thread; class transformation threads never open or
hash JAR files. Maven `pom.properties` is preferred and produces a `groupId:artifactId` name and
version. If Maven metadata is unavailable, manifest and file-name metadata is used with a SHA-1
content identity. Spring Boot nested JAR locations and Maven metadata in exploded class directories
are supported. Archive entry counts, Maven metadata sizes, dependency counts, and fallback hashing
are bounded to protect the application process. At most 1,024 distinct locations and 1,024
dependencies are retained; additional values are ignored with a warning.

```json
{
  "request_type": "app-dependencies-loaded",
  "payload": {
    "dependencies": [
      {
        "name": "org.springframework:spring-webmvc",
        "version": "6.2.1"
      }
    ]
  }
}
```

Dependencies are reported when a class is actually loaded from their code source. This is runtime
discovery, not a static inventory of every file present on the class path. Locations and resolved
dependencies are deduplicated for the lifetime of the process, and the Java agent's own JAR is
excluded. Every event contains all dependencies discovered for the process at that point. A
receiver should replace the previous dependency snapshot for the same `runtime_id`, not merge the
arrays.

### `app-integrations-change`

Emitted after an instrumentation type matcher and Muzzle compatibility check select an application
class and Byte Buddy reports that transformation as successful. It does not imply that the
application has already executed the instrumented method or produced a span. The canonical module
name is reported; configuration aliases are not emitted as separate integrations. Names are
deduplicated across class loaders. Every event contains all integrations applied for the process at
that point. A receiver should replace the previous integration snapshot for the same `runtime_id`,
not merge the arrays.

```json
{
  "request_type": "app-integrations-change",
  "payload": {
    "integrations": [
      {
        "name": "spring-webmvc",
        "enabled": true
      }
    ]
  }
}
```

### `app-heartbeat`

Emitted every 60 seconds by default to report that the process and Meta pipeline are alive. The
event payload is empty because application, runtime, and host identity already exists in the
request-level `resource` object. If other events are ready at the same time, the heartbeat can
share their HTTP batch.

```json
{
  "request_type": "app-heartbeat",
  "payload": {}
}
```

### `app-extended-heartbeat`

Emitted every 24 hours by default. It carries complete current dependency and integration
snapshots, including empty arrays when a collector has no data. This periodic full state lets a
receiver reconstruct state after restarts or lost change-event requests without maintaining
incremental agent-side updates.

```json
{
  "request_type": "app-extended-heartbeat",
  "payload": {
    "configuration": [],
    "dependencies": [
      {
        "name": "org.springframework:spring-webmvc",
        "version": "6.2.1"
      }
    ],
    "integrations": [
      {
        "name": "spring-webmvc",
        "enabled": true
      }
    ]
  }
}
```

`configuration` is currently an empty reserved snapshot. Raw agent configuration is deliberately
not copied because exporter headers and other settings can contain credentials. A future
configuration collector must use an explicit safe allowlist before adding entries.

If an extended heartbeat still has a retryable failure after the HTTP exporter's configured
retries, its full state is sent again during the next ordinary heartbeat cycle. A successful or
permanently failed extended-heartbeat request clears that retry state. The ordinary heartbeat
continues on its normal interval regardless of export success or failure.

Dependency and integration observations that occur during early agent installation are retained
and exported after `app-started`. Under normal load, state changes are sent after
`otel.meta.batch.schedule-delay`; JVM shutdown may trigger export earlier. Meta does not maintain a
general event queue: one wake-up signal and only the latest value of each of the three state event
types are retained. A request therefore contains at most those three state events plus the two
scheduled heartbeat event types. The batching window only combines protocol events into HTTP
requests; it does not change the full-snapshot semantics of dependency and integration payloads.
