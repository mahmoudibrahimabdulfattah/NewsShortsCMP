# R8 rules for the release build.
#
# Only rules this app actually needs are here. Most modern libraries ship their
# own consumer rules inside the AAR — Compose, Coil, Firebase, OkHttp and
# AndroidX all do — and copying their rules in again is how a keep list rots
# into a list of names nobody can explain.
#
# What is left is the code R8 cannot see being used, because it is reached
# through reflection, a ServiceLoader, or generated code.

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# The plugin generates a `Companion.serializer()` for every @Serializable class
# and the runtime looks it up reflectively. R8 sees no caller, so without this
# every network response fails at runtime with SerializationException — and only
# in release, which is the worst place to find out.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# The DTOs themselves: their field names are the wire format, so renaming them
# would silently change the JSON this app can read.
-keep,includedescriptorclasses class com.mk.newsshorts.data.remote.**$$serializer { *; }
-keepclassmembers class com.mk.newsshorts.data.remote.** {
    *** Companion;
}
-keep class com.mk.newsshorts.data.local.SavedArticleDto { *; }
-keep class com.mk.newsshorts.data.local.SavedArticleDto$* { *; }

# ---------------------------------------------------------------------------
# Coroutines
# ---------------------------------------------------------------------------
# The Android main dispatcher is found through a ServiceLoader, and the volatile
# fields below are written by atomic field updaters — R8's optimizer is allowed
# to reorder around fields it thinks nobody touches.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
# Debug-only agent; absent in release, and referenced from a try/catch.
-dontwarn kotlinx.coroutines.debug.**

# ---------------------------------------------------------------------------
# Ktor
# ---------------------------------------------------------------------------
# Engines and plugins register themselves through ServiceLoader entries.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
# Ktor references optional engines and slf4j bindings that are not on the
# classpath here. They are guarded at runtime; the warnings are not.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**

# ---------------------------------------------------------------------------
# Koin
# ---------------------------------------------------------------------------
# Definitions are lambdas, which R8 handles fine — but every type resolved by
# `get()` is looked up by its class, so the classes named in a module must keep
# their identity.
-keep class com.mk.newsshorts.di.** { *; }
-keepnames class com.mk.newsshorts.data.** { *; }
-keepnames class com.mk.newsshorts.domain.** { *; }

# ---------------------------------------------------------------------------
# Firebase / Crashlytics
# ---------------------------------------------------------------------------
# Without these a release crash report is a wall of a.a.a() and tells you
# nothing. The mapping file still has to be uploaded for full deobfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Messaging resolves the service from the manifest by name.
-keep class com.mk.newsshorts.notifications.NewsMessagingService { *; }

# ---------------------------------------------------------------------------
# Credential Manager / Google Sign-In
# ---------------------------------------------------------------------------
# GoogleIdTokenCredential is reconstructed from a Bundle via a static factory
# rather than a normal constructor call R8 can trace.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Firestore sync in this app deliberately reads/writes plain field maps
# (DocumentSnapshot.getString(...), hashMapOf(...)) rather than
# toObject()/POJO mapping, specifically so no reflective model classes are
# needed here at all.

# ---------------------------------------------------------------------------
# Entry points named in the manifest
# ---------------------------------------------------------------------------
-keep class com.mk.newsshorts.MainActivity { *; }
-keep class com.mk.newsshorts.NewsShortsApplication { *; }

# ---------------------------------------------------------------------------
# Reflection metadata
# ---------------------------------------------------------------------------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes *Annotation*

# ---------------------------------------------------------------------------
# Log stripping
# ---------------------------------------------------------------------------
# Nothing in this app logs today, but a library on the way to the network does.
# Removing the calls entirely means a future one cannot leak a URL or a payload
# into logcat, where any app with READ_LOGS on a rooted device can read it.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
