plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[2.3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))
  compileOnly("com.xuxueli:xxl-job-core:2.3.0")

}
