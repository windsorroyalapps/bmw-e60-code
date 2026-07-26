# Keep all BMW app classes
-keep class com.bmwe60coderpro.** { *; }
-keepclassmembers class com.bmwe60coderpro.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# Compose
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.compose.**

# OkHttp / Ktor
-keep class okhttp3.** { *; }
-keep class io.ktor.** { *; }
-dontwarn okhttp3.**
-dontwarn io.ktor.**

# USB Serial
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# General
-keepattributes Signature, Exceptions, *Annotation*
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
