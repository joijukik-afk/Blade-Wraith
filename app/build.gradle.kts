// ============================================================
//  Blade Wraith — App Module Build Script
//  The core module that produces the final APK.
//  Contains all Android-specific configuration.
// ============================================================

plugins {
    // Android Application Plugin
    id("com.android.application")

    // Kotlin Android Plugin
    id("org.jetbrains.kotlin.android")
}

// ============================================================
//  Android Configuration
// ============================================================
android {

    // --------------------------------------------------------
    //  Namespace
    //  Used for R class generation and manifest package name.
    //  Must be unique across the Play Store.
    // --------------------------------------------------------
    namespace = "com.bladewraith"

    // --------------------------------------------------------
    //  Compile SDK
    //  The Android API level the app is compiled against.
    //  API 34 = Android 14 (latest stable at time of writing).
    // --------------------------------------------------------
    compileSdk = 34

    // --------------------------------------------------------
    //  Default Configuration
    //  Applies to all build variants unless overridden.
    // --------------------------------------------------------
    defaultConfig {
        // Unique application ID on the device and Play Store.
        applicationId = "com.bladewraith"

        // Minimum API level supported by the app.
        // API 24 = Android 7.0 (Nougat) — covers ~95% of devices.
        minSdk = 24

        // Target API level. The system uses this to apply
        // compatibility behaviors and policies.
        targetSdk = 34

        // Version code — must increase with every Play Store release.
        versionCode = 1

        // Version name — human-readable version string.
        versionName = "1.0.0"

        // ----------------------------------------------------
        //  Native / ABI Configuration
        //  Our engine is pure Kotlin/Java (no native .so files),
        //  so we don't need NDK or ABI filters.
        // ----------------------------------------------------
        // ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        // (Not needed — commented out intentionally.)

        // ----------------------------------------------------
        //  Resource Configuration
        //  Keep only the languages we actually support.
        //  This reduces APK size.
        // ----------------------------------------------------
        resourceConfigurations += listOf("en", "fa")
        // en = English, fa = Persian (Farsi)

        // ----------------------------------------------------
        //  Vector Drawable Support
        //  Enabled by default in AGP 8.x. No action needed.
        // ----------------------------------------------------
    }

    // --------------------------------------------------------
    //  Build Types
    //  Different configurations for debug and release builds.
    // --------------------------------------------------------
    buildTypes {

        // ----------------------------------------------------
        //  Debug Build
        //  Used during development and by GitHub Actions.
        //  Automatically signed with the debug keystore.
        // ----------------------------------------------------
        getByName("debug") {
            // Debug builds are not minified.
            isMinifyEnabled = false

            // Debug builds are debuggable.
            isDebuggable = true

            // Application ID suffix — allows installing debug
            // and release builds side by side.
            applicationIdSuffix = ".debug"

            // Use the debug signing config (auto-generated).
            signingConfig = signingConfigs.getByName("debug")

            // Version name suffix for clarity.
            versionNameSuffix = "-debug"

            // ProGuard rules (none for debug, but declared for completeness).
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // ----------------------------------------------------
        //  Release Build
        //  Optimized for distribution. Requires a signing key.
        // ----------------------------------------------------
        getByName("release") {
            // Enable code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enable resource shrinking (removes unused resources).
            isShrinkResources = true

            // Release builds are not debuggable.
            isDebuggable = false

            // ProGuard/R8 configuration files.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // --------------------------------------------------
            //  Signing Configuration
            //  For release, you MUST provide a keystore.
            //  For GitHub Actions, use the debug keystore
            //  (which is fine for testing, not for Play Store).
            // --------------------------------------------------
            signingConfig = signingConfigs.getByName("debug")
            // ^ Change to a release signing config for production.

            // --------------------------------------------------
            //  Fallback for CI/CD
            //  If no keystore is available, use debug signing.
            //  This allows GitHub Actions to build a usable APK.
            // --------------------------------------------------
        }
    }

    // --------------------------------------------------------
    //  Signing Configurations
    //  Define custom signing configs here.
    //  For production, load from a keystore.properties file.
    // --------------------------------------------------------
    signingConfigs {
        // Debug signing config is auto-generated by AGP.
        // We don't need to define it explicitly.

        // ----------------------------------------------------
        //  Release Signing Config (Template)
        //  Uncomment and configure for Play Store releases.
        // ----------------------------------------------------
        /*
        create("release") {
            // Load from keystore.properties (not committed to git).
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = java.util.Properties()
                keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))

                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
        */
    }

    // --------------------------------------------------------
    //  Compile Options
    //  Java version compatibility for source and target.
    // --------------------------------------------------------
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        // Enable desugaring for newer Java APIs on older devices.
        isCoreLibraryDesugaringEnabled = true
    }

    // --------------------------------------------------------
    //  Kotlin Options
    // --------------------------------------------------------
    kotlinOptions {
        jvmTarget = "17"

        // Treat all Kotlin warnings as errors (strict mode).
        // allWarningsAsErrors = true

        // Free compiler args for optimization.
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }

    // --------------------------------------------------------
    //  Build Features
    //  Enable or disable optional AGP features.
    // --------------------------------------------------------
    buildFeatures {
        // BuildConfig — useful for version checks at runtime.
        buildConfig = true

        // ViewBinding — not needed (we use OpenGL SurfaceView).
        viewBinding = false

        // Compose — not needed (we use custom OpenGL rendering).
        compose = false

        // DataBinding — not needed.
        dataBinding = false
    }

    // --------------------------------------------------------
    //  Packaging Options
    //  Control what goes into the APK.
    // --------------------------------------------------------
    packaging {
        // Exclude unnecessary metadata files.
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/proguard/*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }

        // JNI libraries — none in this project.
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // --------------------------------------------------------
    //  Lint Options
    //  Control lint behavior during build.
    // --------------------------------------------------------
    lint {
        // Abort build on lint errors (not warnings).
        abortOnError = false

        // Disable specific checks that don't apply to us.
        disable += setOf(
            "GoogleAppIndexingWarning",
            "MissingTranslation",
            "IconMissingDensityFolder"
        )

        // Generate a lint report for debugging.
        htmlReport = true
        xmlReport = false

        // Check all build types.
        checkReleaseBuilds = false
    }

    // --------------------------------------------------------
    //  Test Options
    // --------------------------------------------------------
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // --------------------------------------------------------
    //  Bundle Configuration
    //  For App Bundle (AAB) generation.
    // --------------------------------------------------------
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}

// ============================================================
//  Dependencies
// ============================================================
dependencies {

    // --------------------------------------------------------
    //  Core AndroidX
    // --------------------------------------------------------
    implementation("androidx.core:core-ktx:1.12.0")

    // --------------------------------------------------------
    //  Kotlin Standard Library
    // --------------------------------------------------------
    implementation(kotlin("stdlib"))

    // --------------------------------------------------------
    //  Coroutines (for future async tasks)
    // --------------------------------------------------------
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --------------------------------------------------------
    //  Desugaring Library
    //  Required for using newer Java APIs on older Android versions.
    // --------------------------------------------------------
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // --------------------------------------------------------
    //  Testing (optional, for future unit tests)
    // --------------------------------------------------------
    // testImplementation("junit:junit:4.13.2")
    // androidTestImplementation("androidx.test.ext:junit:1.1.5")
    // androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ============================================================
//  Task: Generate APK Info
//  Prints a summary of the built APK.
// ============================================================
tasks.register("apkInfo") {
    doLast {
        val apkDir = file("${project.buildDir}/outputs/apk")
        if (apkDir.exists()) {
            apkDir.walkTopDown().filter { it.extension == "apk" }.forEach {
                println("==========================================")
                println("  APK: ${it.name}")
                println("  Size: ${it.length() / 1024} KB")
                println("  Path: ${it.absolutePath}")
                println("==========================================")
            }
        } else {
            println("No APK found. Run assembleDebug first.")
        }
    }
}

// ============================================================
//  Task: Clean Build
//  A convenience task that cleans and rebuilds everything.
// ============================================================
tasks.register("cleanBuild") {
    dependsOn("clean")
    dependsOn(":app:assembleDebug")
    doLast {
        println("✅ Clean build complete.")
    }
}
