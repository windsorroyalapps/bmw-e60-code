# Intentionally minimal for initial builds

# Keep usb-serial library classes
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }
