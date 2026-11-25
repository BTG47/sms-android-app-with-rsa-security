import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":crypto"))
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    mainClass.set("com.securesms.benchmark.BenchmarkMainKt")
}

// ./gradlew :benchmark:run --args="--out ../results/jvm"
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// ./gradlew :benchmark:generateVectors  -> test-vectors/vectors.json (+ copy into crypto test resources)
tasks.register<JavaExec>("generateVectors") {
    group = "securesms"
    description = "Generates sample envelopes with expected results for Android integration."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.securesms.benchmark.GenerateVectorsKt")
    workingDir = rootProject.projectDir
    args("test-vectors/vectors.json", "crypto/src/test/resources/vectors.json")
}
