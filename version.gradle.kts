val stableVersion = "2.11.1"
val alphaVersion = "2.11.1-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
