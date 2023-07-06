plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba")
    module.set("dubbo")
    versions.set("[2.6.0,2.8.4]")
    assertInverse.set(true)
  }
}


dependencies {
  implementation(project(":instrumentation:alibaba-dubbo-2.6:library-autoconfigure"))
  library("com.alibaba:dubbo:2.6.12")
}
