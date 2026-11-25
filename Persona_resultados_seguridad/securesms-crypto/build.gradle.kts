plugins {
    // Version declared once here; sub-modules apply the plugin without a version so the
    // :crypto module can also be included from Bruno's Android build (which declares its own).
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    group = "com.securesms"
    version = "1.0.1"
    repositories { mavenCentral() }
}
