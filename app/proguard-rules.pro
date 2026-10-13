# ============================================================
#  Blade Wraith — ProGuard / R8 Rules
#  Optimized for a Kotlin + OpenGL ES 2.0 game.
#  Only applied in release builds (isMinifyEnabled = true).
# ============================================================

# ============================================================
#  General Optimizations
# ============================================================

# Keep line numbers for debugging stack traces (helps crash reports).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations (needed by some libraries at runtime).
-keepattributes *Annotation*

# Keep generic signatures for reflection-based code.
-keepattributes Signature

# Keep inner class metadata.
-keepattributes InnerClasses

# Keep enclosing method info.
-keepattributes EnclosingMethod

# Keep exceptions metadata.
-keepattributes Exceptions

# ============================================================
#  Repackaging & Optimization
# ============================================================

# Aggressive optimization passes.
-optimizationpasses 5

# Allow access modification (safer obfuscation).
-allowaccessmodification

# Don't pre-verify (faster build, smaller output).
-dontpreverify

# Remove logging in release builds (Comment out if you need logs).
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# Keep info-level logs for crash diagnosis (optional).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# ============================================================
#  Kotlin Standard Library
# ============================================================

# Kotlin metadata is needed for reflection (rare in games).
-keep class kotlin.Metadata { *; }

# Kotlin coroutines (if used in the future).
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Kotlin companion objects.
-keepclassmembers class **$Companion {
    <methods>;
}

# Kotlin default interface methods.
-keep class kotlin.jvm.internal.DefaultConstructorMarker { *; }

# Kotlin Intrinsics — used everywhere, keep to avoid issues.
-keep class kotlin.jvm.internal.Intrinsics { *; }

# Kotlin reflection (only if used).
-dontwarn kotlin.reflect.**

# ============================================================
#  Android Framework
# ============================================================

# Keep the Application class (if defined).
-keep public class * extends android.app.Application

# Keep Activity classes referenced in AndroidManifest.xml.
-keep public class * extends android.app.Activity {
    public <init>(...);
}

# Keep Service classes.
-keep public class * extends android.app.Service

# Keep BroadcastReceiver classes.
-keep public class * extends android.content.BroadcastReceiver

# Keep ContentProvider classes.
-keep public class * extends android.content.ContentProvider

# Keep View classes inflated from XML.
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep custom View constructors (used by XML inflation).
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep classes with onClick handlers (if XML-referenced).
-keepclassmembers class * extends android.content.Context {
    public void *(android.view.View);
    public void *(android.view.MenuItem);
}

# Keep Parcelable implementations.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enum values() and valueOf() methods.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods (JNI).
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
#  OpenGL ES
# ============================================================

# OpenGL is accessed via Java API — R8 must not strip these classes.
-keep class android.opengl.** { *; }
-keep class javax.microedition.khronos.** { *; }

# Keep GLSurfaceView subclasses (referenced from code).
-keep class * extends android.opengl.GLSurfaceView {
    public <init>(...);
}

# Keep GLSurfaceView.Renderer implementations.
-keep class * implements android.opengl.GLSurfaceView$Renderer {
    public <init>(...);
}

# Keep GLSurfaceView inner classes.
-keep class android.opengl.GLSurfaceView$* { *; }

# ============================================================
#  Blade Wraith — Engine Classes
#  We keep ALL engine classes to prevent accidental stripping.
#  The engine uses reflection-free code, but keeping them
#  ensures no surprises during heavy obfuscation.
# ============================================================

# Keep entire engine package structure.
-keep class com.bladewraith.engine.** { *; }
-keep class com.bladewraith.core.** { *; }

# Keep entire game package (entities, state, logic).
-keep class com.bladewraith.game.** { *; }

# Keep MainActivity (entry point, referenced in manifest).
-keep class com.bladewraith.MainActivity { *; }

# Keep GameSurfaceView (custom surface view).
-keep class com.bladewraith.GameSurfaceView { *; }

# Keep all inner classes and nested types.
-keep class com.bladewraith.**$* { *; }

# Keep all companion objects in our code.
-keepclassmembers class com.bladewraith.** {
    <methods>;
    <fields>;
}

# Keep all enums we define.
-keep class com.bladewraith.**$* {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native entry points (none currently, but future-proof).
-keepclasseswithmembers class com.bladewraith.** {
    native <methods>;
}

# ============================================================
#  Reflection / Serialization Safety
#  Our engine avoids reflection, but keeping these rules
#  protects against future additions.
# ============================================================

# Keep class names (helps with debugging).
-keepnames class com.bladewraith.**

# Keep method names for stack traces.
-keepclassmembers class com.bladewraith.** {
    public <methods>;
    protected <methods>;
}

# ============================================================
#  Suppress Warnings for Optional Dependencies
#  These classes are referenced but not always present.
# ============================================================

# Kotlin stdlib optional internals.
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Java 8+ optional APIs.
-dontwarn java.lang.invoke.**
-dontwarn java.util.function.**
-dontwarn javax.annotation.**

# AndroidX optional parts.
-dontwarn androidx.**

# ============================================================
#  Attribute Preservation
#  Keep information needed for crash deobfuscation.
# ============================================================

# Retain line numbers and source file names for stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute Source

# Retain signatures for reflection-heavy code.
-keepattributes Signature

# Retain annotations.
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# ============================================================
#  Debugging Support
#  Useful for release builds — remove for maximum obfuscation.
# ============================================================

# Keep our package names visible in stack traces.
-repackageclasses 'com.bladewraith'
-flattenpackagehierarchy

# Note: The above line moves all obfuscated classes into
# com.bladewraith package, making stack traces easier to read.

# ============================================================
#  R8-Specific Rules
#  Only apply when R8 is used (default in AGP 8+).
# ============================================================

# Don't merge classes that could conflict.
-keep,allowshrinking,allowoptimization class com.bladewraith.** { *; }

# ============================================================
#  Resource Rules
#  These are handled by shrinkResources, not ProGuard.
# ============================================================

# (No resource rules needed here.)

# ============================================================
#  End of ProGuard Rules
#  Everything below is optional and can be removed if
#  a cleaner obfuscation is desired.
# ============================================================
