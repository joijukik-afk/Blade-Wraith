// ============================================================
//  Blade Wraith — Root Build Configuration
// ============================================================

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

// ============================================================
//  NOTE: repositories are declared ONLY in settings.gradle.kts.
//  We removed the `allprojects { repositories { ... } }` block
//  because settings.gradle.kts has FAIL_ON_PROJECT_REPOS
//  enabled, which forbids project-level repositories.
// ============================================================

// ============================================================
//  Build directory layout
// ============================================================
rootProject.buildDir = file("${rootProject.projectDir}/build")
subprojects {
    buildDir = file("${rootProject.buildDir}/${project.name}")
}

// ============================================================
//  Clean task
// ============================================================
tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
    delete(file("${rootProject.projectDir}/.gradle"))
    delete(file("${rootProject.projectDir}/.kotlin"))
}

// ============================================================
//  Diagnostics
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
