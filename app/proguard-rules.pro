# Transiva Merchant release hardening rules.
# Enable minifyEnabled/shrinkResources only after signed release regression testing.
-keepattributes Signature,*Annotation*
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keep class org.json.** { *; }

# Transiva Merchant release hardening compatibility
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-dontwarn org.conscrypt.**
-dontwarn javax.annotation.**

# Firebase components are discovered by manifest / Google services.
-keep class com.google.firebase.** { *; }

# MapLibre uses JNI/reflection across native boundaries.
-keep class org.maplibre.** { *; }
-keep interface org.maplibre.** { *; }

# Keep Transiva Firebase service entry point explicit.
-keep class com.transiva.app.TransivaFirebaseService { *; }
