-keepattributes SourceFile,LineNumberTable
-keep class com.localmacrotracker.app.data.** { *; }
-keep class com.localmacrotracker.app.llm.model.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
# MediaPipe
-keep class com.google.mediapipe.** { *; }
# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** serializer();
    kotlinx.serialization.KSerializer serializer(...);
}
