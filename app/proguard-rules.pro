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

# WebRTC Java and native entry points for the Merchant call screen.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
# WebRTC 144 JNI_OnLoad also looks up org/jni_zero/JniInit by its Java name.
-keep class org.jni_zero.** { *; }
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.jni_zero.CalledByNative <methods>;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.jni_zero.CalledByNativeUnchecked <methods>;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.jni_zero.AccessedByNative <fields>;
}
-keep class * implements org.webrtc.PeerConnection$Observer { *; }
-keep class * implements org.webrtc.SdpObserver { *; }
-keep class * implements org.webrtc.VideoSink { *; }
-keep class com.transiva.app.WebRtcNativeRuntime { *; }
-keep class com.transiva.app.WebRtcSessionEngine { *; }
-keep class com.transiva.app.WebRtcSessionEngine$* { *; }
