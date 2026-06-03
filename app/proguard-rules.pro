-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# NanoHTTPD — embedded HTTP server
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Java-WebSocket
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Android SpeechRecognizer
-keep class android.speech.** { *; }

# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# App model classes
-keep class com.example.recorderproject.model.** { *; }
-keep class com.example.recorderproject.data.** { *; }
