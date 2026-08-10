# Transiva Merchant release hardening rules.
# Enable minifyEnabled/shrinkResources only after signed release regression testing.
-keepattributes Signature,*Annotation*
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keep class org.json.** { *; }
