# Dev builds with on-device AI only (see build.gradle.kts). Google's ML Kit GenAI finds parts of itself by reflection,
# and fails with a NullPointerException in Generation.getClient() when the shrinker renames or removes them.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.apps.aicore.** { *; }
-keep class com.google.ai.edge.aicore.** { *; }
