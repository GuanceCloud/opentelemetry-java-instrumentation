val stableVersion = "2.30.2"
val alphaVersion = "2.30.2-alpha"

val apidiffBaselineVersion = "2.29.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
