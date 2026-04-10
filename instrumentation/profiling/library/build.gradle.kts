import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
  id("otel.library-instrumentation")
}

val mrJarVersions = listOf(11, 17)
val javaToolchainService = project.extensions.getByType(JavaToolchainService::class.java)

sourceSets {
  create("testJava11") {
    java {
      setSrcDirs(listOf("src/testJava11/java"))
    }
  }
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
  named("testJava11Implementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("testJava11RuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
  named("testJava17Implementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("testJava17RuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
}

dependencies {
  add("testJava11Implementation", sourceSets.test.get().output)
  add("testJava11Implementation", sourceSets["java11"].output)
  add("testJava11Implementation", sourceSets.main.get().output)
  add("testJava17Implementation", sourceSets.test.get().output)
  add("testJava17Implementation", sourceSets["java11"].output)
  add("testJava17Implementation", sourceSets["java17"].output)
  add("testJava17Implementation", sourceSets.main.get().output)
}

tasks {
  named<JavaCompile>("compileTestJava11Java") {
    dependsOn("compileJava11Java")
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.release.set(11)
  }

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

  val testJava11 by registering(Test::class) {
    dependsOn("compileTestJava11Java")
    testClassesDirs = sourceSets["testJava11"].output.classesDirs
    classpath = sourceSets["testJava11"].runtimeClasspath
    javaLauncher.set(
      javaToolchainService.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
      }
    )
  }

  val testJava17 by registering(Test::class) {
    dependsOn("compileTestJava17Java")
    testClassesDirs = sourceSets["testJava17"].output.classesDirs
    classpath = sourceSets["testJava17"].runtimeClasspath
    javaLauncher.set(
      javaToolchainService.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
      }
    )
  }

  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
    named("testJava11", Test::class).configure {
      enabled = false
    }
  }
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
    named("testJava17", Test::class).configure {
      enabled = false
    }
  }

  check {
    dependsOn(testJava11)
    dependsOn(testJava17)
  }
}
