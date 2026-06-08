# Keep data models
-keep class com.messagingapp.data.models.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Kotlin Serialization
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keepattributes RuntimeVisibleAnnotations

# Keep Supabase and Ktor classes
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.ktor.utils.io.**
-dontwarn org.slf4j.**

# Keep Composable functions
-keep class androidx.compose.** { *; }
-keep class com.messagingapp.ui.** { *; }

# Optimization rules
-optimizations !code/simplification/arithmetic
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
