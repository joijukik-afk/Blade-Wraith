// ============================================================
//  Blade Wraith — Gradle Settings
//  Configures plugin management, dependency resolution,
//  and project structure for the entire build.
// ============================================================

// ============================================================
//  Plugin Management
//  Controls where Gradle looks for plugins and their versions.
//  MUST be the first block in this file.
// ============================================================
pluginManagement {
    repositories {
        // Google's Maven repository — hosts Android Gradle Plugin
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }

        // Maven Central — main repository for Kotlin and general libs
        mavenCentral()

        // Gradle Plugin Portal — for community Gradle plugins
        gradlePluginPortal()
    }

    // Enforce plugin versions to prevent accidental upgrades
    plugins {
        // (Versions are declared in root build.gradle.kts)
    }
}

// ============================================================
//  Dependency Resolution Management
//  Centralizes repositories so sub-projects cannot define their own.
// ============================================================
dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS: sub-projects must NOT declare repositories
    // This enforces a single source of truth for dependencies.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// ============================================================
//  Build Cache Configuration
//  Speeds up incremental builds across modules.
// ============================================================
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

// ============================================================
//  Root Project Name
// ============================================================
rootProject.name = "BladeWraith"

// ============================================================
//  Included Modules
//  Only `app` for now. Future modules (engine, editor) go here.
// ============================================================
include(":app")

// ============================================================
//  Feature Preview Flags
// ============================================================
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ============================================================
//  Project-level properties
// ============================================================
gradle.rootProject {
    group = "com.bladewraith"
    version = "1.0.0"
    description = "Blade Wraith — Offline 2D Boss-Fight Game for Android"
}

// ============================================================
//  Diagnostics: log repositories being used
//  (Helpful when debugging dependency resolution issues)
// ============================================================
gradle.settingsEvaluated {
    println("==========================================")
    println("  Blade Wraith — Settings Evaluated")
    println("==========================================")
    println("  Root project : $rootProject.name")
    println("  Root dir     : $rootDir")
    println("  Modules      : ${rootProject.children.map { it.name }}")
    println("==========================================")
}
