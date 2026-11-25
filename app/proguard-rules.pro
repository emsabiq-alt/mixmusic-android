# EmsaMusic Proguard Rules
-keep class com.emsabiq.emsamusic.models.** { *; }
-keepclassmembers class * {
    @androidx.media3.common.util.UnstableApi *;
}
