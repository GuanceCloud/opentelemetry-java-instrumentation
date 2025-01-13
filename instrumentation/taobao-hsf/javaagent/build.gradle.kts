plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba.middleware")
    module.set("hsf-sdk")
    versions.set("[2.2.8.2--2019-06-stable,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.alibaba.middleware:hsf-sdk:2.2.8.2--2019-06-stable")
  implementation(project(":instrumentation:taobao-hsf:library"))
}
