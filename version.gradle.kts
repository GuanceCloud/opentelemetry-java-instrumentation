val stableVersion = "2.30.1"
val alphaVersion = "2.30.1-alpha"

val apidiffBaselineVersion = "2.29.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
