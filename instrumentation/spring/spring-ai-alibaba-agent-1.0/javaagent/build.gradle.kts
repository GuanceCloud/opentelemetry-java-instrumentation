plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba.cloud.ai")
    module.set("spring-ai-alibaba-agent-framework")
    versions.set("[1.1.2.0,2)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.alibaba.cloud.ai:spring-ai-alibaba-agent-framework:1.1.2.0")
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  testInstrumentation(project(":instrumentation:spring:spring-ai-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}
