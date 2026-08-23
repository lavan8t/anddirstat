# Proguard / R8 Full Mode Tree-Shaking Rules for AndDirStat

# Keep Compose and Reflection Metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep CompactNode and data models
-keep class com.kd.anddirstat.model.** { *; }

# Allow R8 aggressive inlining and optimization
-allowaccessmodification
-repackageclasses 'a'
-dontusemixedcaseclassnames

# Strip debug logging calls
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# AndroidX & Material
-dontwarn com.google.android.material.**
-dontwarn androidx.**
