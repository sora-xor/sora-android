# General
-keepattributes SourceFile,LineNumberTable,*Annotation*,EnclosingMethod,Signature,Exceptions,InnerClasses

#Firebase Crashlytics
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep,includedescriptorclasses public class * extends java.lang.Exception
-keep,includedescriptorclasses class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*
# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

-keep,includedescriptorclasses class kotlin.reflect.jvm.internal.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-dontwarn sun.misc.Unsafe
-dontwarn com.google.**
-dontwarn javax.naming.**
-dontwarn org.jetbrains.annotations.**

# Okhttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.*
-dontwarn okio.**
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.conscrypt.ConscryptHostnameVerifier
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Retrofit
# https://github.com/square/retrofit/blob/master/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro
-keepattributes Signature
-keepattributes Exceptions

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers enum * { *; }

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Gson specific classes
-dontwarn sun.misc.**

# Prevent proguard from stripping interface information from TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class org.apache.xerces.**

# Encryption
-keep class jp.co.soramitsu.crypto.** { *; }
-keep class org.spongycastle.** { *; }

# Keep sora sdk classes
-keep class jp.co.soramitsu.sora.sdk.** { *; }
-keep class jp.co.soramitsu.fearless_utils.** { *; }
-keep class net.jpountz.** { *; }
