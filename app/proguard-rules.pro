# ---------------------------------------------------------------------------
# P1 (§18.1): user financial data must NEVER reach Logcat.
#
# Android strips Log.* from release builds only if you make it an error here.
# Adding -assumenosideeffects makes any accidental Log call a BUILD FAILURE in
# release, so a stray log of an amount cannot ship even by accident.
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int v(...);
    public static int d(...);
    public static int d(...);
    public static int i(...);
    public static int i(...);
    public static int w(...);
    public static int w(...);
    public static int e(...);
    public static int e(...);
    public static int wtf(...);
    public static int wtf(...);
}

# --- Firebase / Firestore ---
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*
-keepclassmembers class com.srinidhi.kharch.** {
    *** Companion;
}
-keepclasseswithmembers class com.srinidhi.kharch.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.srinidhi.kharch.**$$serializer { *; }

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Glance widget ---
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# --- Model classes (R8 must not strip Room/Firestore data classes) ---
-keep class com.srinidhi.kharch.core.model.** { *; }
-keep class com.srinidhi.kharch.data.remote.dto.** { *; }

# --- Strip verbose logging in release ---
-assumenosideeffects class java.util.logging.Logger {
    public static void log(java.util.logging.Level, java.lang.String);
}