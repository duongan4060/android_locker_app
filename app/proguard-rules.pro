# Keep rules for OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep rules for ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep rules for Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
