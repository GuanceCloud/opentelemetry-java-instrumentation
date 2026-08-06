# Async-profiler Integration

This module embeds async-profiler into the OpenTelemetry Java agent on Linux.

Current scope:

- Linux only
- `jfr` file export
- `datakit` multipart upload
- `otlp` HTTP/protobuf upload
- `pprof` compatibility mode backed by async-profiler OTLP payloads
- Profiling starts during agent startup and stops during JVM shutdown

Configuration uses the `otel.profiling.*` namespace:

| System property                         | Environment variable                       | Type    | Default                                       | Description                                  |
| --------------------------------------- | ------------------------------------------ | ------- | --------------------------------------------- | -------------------------------------------- |
| `otel.profiling.enabled`                | `OTEL_PROFILING_ENABLED`                   | Boolean | `false`                                       | Enables async-profiler integration.          |
| `otel.profiling.exporter`               | `OTEL_PROFILING_EXPORTER`                  | String  | `file`                                        | Export target: `file`, `datakit`, `otlp`, or `pprof`. |
| `otel.profiling.endpoint`               | `OTEL_PROFILING_ENDPOINT`                  | String  | `http://localhost:9529/profiling/v1/input`    | Datakit profiling upload endpoint.           |
| `otel.profiling.sample-interval`        | `OTEL_PROFILING_SAMPLE_INTERVAL`           | String  | async-profiler default                        | Sampling interval for the primary event.     |
| `otel.profiling.export-interval`        | `OTEL_PROFILING_EXPORT_INTERVAL`           | String  | `60s`                                         | Rotation interval. `0s` means export only on shutdown. |
| `otel.profiling.max-frames`             | `OTEL_PROFILING_MAX_FRAMES`                | Integer | async-profiler default                        | Max stack depth (`jstackdepth`).             |
| `otel.profiling.exception.enabled`      | `OTEL_PROFILING_EXCEPTION_ENABLED`         | Boolean | `false`                                       | Enables `jdk.JavaExceptionThrow` JFR sync events. |
| `otel.profiling.exception.sampling-interval` | `OTEL_PROFILING_EXCEPTION_SAMPLING_INTERVAL` | String | async-profiler default                        | Exception event threshold, mapped to JFR `threshold`. |
| `otel.profiling.exception.collect-message` | `OTEL_PROFILING_EXCEPTION_COLLECT_MESSAGE` | Boolean | `true`                                        | Currently ignored. JFR exception events always carry the message. |
| `otel.profiling.lock.enabled`           | `OTEL_PROFILING_LOCK_ENABLED`              | Boolean | `true`                                        | Enables lock profiling. Non-JFR output uses `lock` as the primary event. |
| `otel.profiling.memory.enabled`         | `OTEL_PROFILING_MEMORY_ENABLED`            | Boolean | `true`                                        | Enables continuous memory profiling. For JFR output it enables allocation profiling for each export interval; for non-JFR output it switches the primary event to `alloc`. |
| `otel.profiling.memory.interval`        | `OTEL_PROFILING_MEMORY_INTERVAL`           | String  | async-profiler default                        | Allocation interval, e.g. `128k`.            |
| `otel.profiling.memory.top-stats`       | `OTEL_PROFILING_MEMORY_TOP_STATS`          | Integer | `0`                                           | For JFR output, values greater than `0` additionally enable `profiler.LiveObject` events as a supplemental live-object view. Ignored for non-JFR output. |
| `otel.profiling.pprof.path`             | `OTEL_PROFILING_PPROF_PATH`                | String  | `${java.io.tmpdir}/opentelemetry/profiles/profile-%p.pprof` | Output file for `pprof` compatibility mode. |
| `otel.profiling.pprof.upload-url`       | `OTEL_PROFILING_PPROF_UPLOAD_URL`          | String  | unset                                         | Upload URL for `pprof` compatibility mode.   |
| `otel.profiling.pprof.headers`          | `OTEL_PROFILING_PPROF_HEADERS`             | String  | unset                                         | Comma-separated request headers for `pprof` compatibility mode. |
| `otel.profiling.async.event`            | `OTEL_PROFILING_ASYNC_EVENT`               | String  | `cpu`                                         | Async-profiler event, e.g. `cpu`, `wall`, or `alloc`. |
| `otel.profiling.async.startup-delay`    | `OTEL_PROFILING_ASYNC_STARTUP_DELAY`       | String  | `0s`                                          | Delay before profiling starts.               |
| `otel.profiling.async.output`           | `OTEL_PROFILING_ASYNC_OUTPUT`              | String  | `${java.io.tmpdir}/opentelemetry/profiles/profile-%p.jfr` | JFR output file pattern.                     |
| `otel.profiling.async.temp-dir`         | `OTEL_PROFILING_ASYNC_TEMP_DIR`            | String  | `${java.io.tmpdir}/opentelemetry/async-profiler` | Directory used to extract the native library. |
| `otel.exporter.otlp.profiles.protocol`  | `OTEL_EXPORTER_OTLP_PROFILES_PROTOCOL`     | String  | `http/protobuf`                               | OTLP profiles protocol. Only `http/protobuf` is supported. |
| `otel.exporter.otlp.profiles.endpoint`  | `OTEL_EXPORTER_OTLP_PROFILES_ENDPOINT`     | String  | `http://localhost:4318/v1/profiles`           | OTLP profiles endpoint.                      |
| `otel.exporter.otlp.profiles.headers`   | `OTEL_EXPORTER_OTLP_PROFILES_HEADERS`      | String  | unset                                         | Comma-separated OTLP profiles headers.       |

Notes:

- async-profiler 4.4 does not support native `pprof` output. `OTEL_PROFILING_EXPORTER=pprof` is a compatibility mode that emits async-profiler OTLP payloads.
- Non-JFR output cannot multiplex multiple async-profiler events. When `OTEL_PROFILING_MEMORY_ENABLED=true`, the primary event switches to `alloc`. When `OTEL_PROFILING_LOCK_ENABLED=true`, the primary event switches to `lock`.
- JFR output with `OTEL_PROFILING_MEMORY_ENABLED=true` captures allocation events such as `jdk.ObjectAllocationInNewTLAB` and `jdk.ObjectAllocationOutsideTLAB` for each export interval, which is the primary continuous memory profiling signal.
- If `OTEL_PROFILING_MEMORY_TOP_STATS>0`, JFR output additionally includes `profiler.LiveObject` events as a supplemental live-object view.
- `OTEL_PROFILING_EXCEPTION_*` works only with JFR output.
- 默认会每 `60s` 轮转一次，行为更接近 Datadog 的 continuous profiling。若只想在进程退出时导出一次，显式设置 `OTEL_PROFILING_EXPORT_INTERVAL=0s`。
- 如果你希望本地保留每一轮文件，输出路径里带上 `%t`，例如 `/tmp/profile-%p-%t.jfr`。

Example:

```bash
export OTEL_PROFILING_ENABLED=true
export OTEL_PROFILING_EXPORTER=otlp
export OTEL_PROFILING_SAMPLE_INTERVAL=10ms
export OTEL_PROFILING_EXPORT_INTERVAL=30s
export OTEL_PROFILING_MAX_FRAMES=256
export OTEL_PROFILING_MEMORY_ENABLED=true
export OTEL_PROFILING_MEMORY_INTERVAL=128k
export OTEL_EXPORTER_OTLP_PROFILES_PROTOCOL=http/protobuf
export OTEL_EXPORTER_OTLP_PROFILES_ENDPOINT=http://127.0.0.1:4318/v1/profiles
export OTEL_EXPORTER_OTLP_PROFILES_HEADERS=Authorization=Bearer token

java \
  -javaagent:path/to/opentelemetry-javaagent.jar \
  -jar app.jar
```
