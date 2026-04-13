plugins {
  id("otel.javaagent-instrumentation")
}

val profilingTestOutputDir = layout.buildDirectory.dir("test-profile-output")
val profilingTestLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) }
val profilingDatakitTestPort = 19529

dependencies {
  implementation(project(":instrumentation:profiling:library"))
  implementation(project(":instrumentation-api-incubator"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}

tasks {
  test {
    val outputDir = profilingTestOutputDir.get().asFile

    javaLauncher.set(profilingTestLauncher)

    filter {
      excludeTestsMatching("io.opentelemetry.javaagent.instrumentation.profiling.ProfilingDatakitExportTest")
    }

    doFirst {
      outputDir.deleteRecursively()
      outputDir.mkdirs()
    }

    environment("OTEL_PROFILING_ENABLED", "true")
    environment("OTEL_PROFILING_EXPORTER", "file")
    environment("OTEL_PROFILING_INTERVAL", "1s")
    environment("OTEL_PROFILING_STARTUP_DELAY", "0s")
    environment("OTEL_PROFILING_EXPERIMENTAL_FILE_EXPORT_PATH", outputDir.absolutePath)
  }

  val datakitTest by registering(Test::class) {
    javaLauncher.set(profilingTestLauncher)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("io.opentelemetry.javaagent.instrumentation.profiling.ProfilingDatakitExportTest")
    }

    environment("OTEL_PROFILING_ENABLED", "true")
    environment("OTEL_PROFILING_EXPORTER", "datakit")
    environment("OTEL_PROFILING_INTERVAL", "1s")
    environment("OTEL_PROFILING_STARTUP_DELAY", "2s")
    environment("OTEL_PROFILING_MEMORY_ENABLED", "true")
    systemProperty(
      "otel.profiling.endpoint",
      "http://127.0.0.1:$profilingDatakitTestPort/profiling/v1/input"
    )
    systemProperty("otel.service.name", "profiling-test-service")
    systemProperty("otel.service.version", "1.2.3")
    systemProperty(
      "otel.resource.attributes",
      "deployment.environment.name=prod,host.name=profiling-host"
    )
    systemProperty("profiling.datakit.test.port", profilingDatakitTestPort.toString())
  }

  check {
    dependsOn(datakitTest)
  }
}
