plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.9.3,)")
    assertInverse.set(true)
  }
}


dependencies {
  implementation(project(":instrumentation:apache-thrift:library-autoconfigure"))
  library("org.apache.thrift:libthrift:0.9.3")
}
