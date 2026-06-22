# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK proguard-android-optimize.txt file.

# Keep Kotlin metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep WorkManager workers
-keep class com.orty.copilotpulse.RefreshWorker { *; }

# Keep AppWidgetProvider subclasses
-keep class com.orty.copilotpulse.PulseWidget { *; }
