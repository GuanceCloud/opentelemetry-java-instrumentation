plugins {
  id("otel.library-instrumentation")
}

val mrJarVersions = listOf(17)

sourceSets {
  create("testJava17") {
    java {
      setSrcDirs(listOf("src/testJava17/java"))
    }
  }
}

for (version in mrJarVersions) {
  sourceSets {
    create("java$version") {
      java {
        setSrcDirs(listOf("src/main/java$version"))
      }
    }
  }

  tasks {
    named<JavaCompile>("compileJava${version}Java") {
      sourceCompatibility = "$version"
      targetCompatibility = "$version"
      options.release.set(version)
    }
  }

  configurations {
    named("java${version}Implementation") {
      extendsFrom(configurations["implementation"])
    }
    named("java${version}CompileOnly") {
      extendsFrom(configurations["compileOnly"])
    }
  }

  dependencies {
    add("java${version}Implementation", files(sourceSets.main.get().output.classesDirs))
  }
}

configurations {
  named("testJava17Implementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("testJava17RuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
}

dependencies {
  add("testJava17Implementation", sourceSets.test.get().output)
  add("testJava17Implementation", sourceSets["java17"].output)
  add("testJava17Implementation", sourceSets.main.get().output)
}

tasks {
  named<JavaCompile>("compileTestJava17Java") {
    dependsOn("compileJava17Java")
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  withType(Jar::class) {
    val sourcePathProvider = if (name == "jar") {
      { ss: SourceSet? -> ss?.output }
    } else if (name == "sourcesJar") {
      { ss: SourceSet? -> ss?.java }
    } else {
      { project.objects.fileCollection() }
    }

    for (version in mrJarVersions) {
      into("META-INF/versions/$version") {
        from(sourcePathProvider(sourceSets["java$version"]))
      }
    }
    manifest.attributes("Multi-Release" to "true")
  }

  val testJava17 by registering(Test::class) {
    dependsOn("compileTestJava17Java")
    testClassesDirs = sourceSets["testJava17"].output.classesDirs
    classpath = sourceSets["testJava17"].runtimeClasspath
  }

  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
    named("testJava17", Test::class).configure {
      enabled = false
    }
  }

  check {
    dependsOn(testJava17)
  }
}
