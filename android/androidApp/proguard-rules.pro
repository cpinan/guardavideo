# --- youtubedl-android (yt-dlp + Python + ffmpeg) ---
# It ships no consumer rules. Jackson maps yt-dlp's JSON into these classes by reflection, and the
# library extracts its Python/ffmpeg payloads with commons-compress.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**

# --- Ktor (optional JVM integrations it references but Android does not have) ---
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
