# ML Kit pose detection rules (for release minification hardening).
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_pose_bundled.**

# Keep app-side pose pipeline callback types.
-keep class com.inversioncoach.app.pose.** { *; }
-keep class com.inversioncoach.app.model.PoseFrame { *; }
