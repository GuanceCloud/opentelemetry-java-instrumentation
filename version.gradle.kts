val stableVersion = "1.26.1-guance"
val alphaVersion = "1.26.1-guance-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
