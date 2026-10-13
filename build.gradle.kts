// ============================================================
//  Blade Wraith — Root Build Configuration
//  A high-performance offline boss-fight game for Android
//  Engine: Custom OpenGL ES 2.0 | Language: Kotlin
// ============================================================

plugins {
    // Android Gradle Plugin — stable release
    id("com.android.application") version "8.2.2" apply false

    // Kotlin Android Plugin — compatible with AGP 8.2.2
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false

    // Kotlin Kapt (optional, for future annotation processing)
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
}

// ============================================================
//  Global configuration for all sub-projects
// ============================================================
allprojects {
    // Central repositories
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// ============================================================
//  Build directory layout — keeps all build outputs in one place
// ============================================================
rootProject.buildDir = file("${rootProject.projectDir}/build")
subprojects {
    buildDir = file("${rootProject.buildDir}/${project.name}")
}

// ============================================================
//  Task: clean — removes all build artifacts
// ============================================================
tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
    delete(file("${rootProject.projectDir}/.gradle"))
    delete(file("${rootProject.projectDir}/.kotlin"))
}

// ============================================================
//  Task: printVersions — quick diagnostics
// ============================================================
tasks.register("printVersions") {
    doLast {
        println("==========================================")
        println("  Blade Wraith — Build Environment")
        println("==========================================")
        println("  Gradle      : ${gradle.gradleVersion}")
        println("  JVM         : ${System.getProperty("java.version")}")
        println("  OS          : ${System.getProperty("os.name")}")
        println("  Project Dir : ${rootProject.projectDir}")
        println("==========================================")
    }
}

// ============================================================
//  Task: checkEnvironment — validates toolchain
// ============================================================
tasks.register("checkEnvironment") {
    doLast {
        val javaVersion = System.getProperty("java.version")
        val major = javaVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
        if (major < 17 && !javaVersion.startsWith("17")) {
            println("⚠  Warning: Java 17+ is recommended. Found: $javaVersion")
        } else {
            println("✅ Java version OK: $javaVersion")
        }
    }
}
