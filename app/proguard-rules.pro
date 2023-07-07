# General
-keepattributes SourceFile,LineNumberTable,*Annotation*,EnclosingMethod,Signature,Exceptions,InnerClasses

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile
#-repackageclasses
#-allowaccessmodification

#Firebase Crashlytics
-keep,includedescriptorclasses public class * extends java.lang.Exception

# Gson
-keep,allowobfuscation,allowoptimization class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class sun.misc.Unsafe { *; }
-keepclassmembers enum * { *; }
-dontwarn sun.misc.**

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

-keep class org.apache.xerces.**

# Encryption
-keep class jp.co.soramitsu.crypto.** { *; }
-keep class org.spongycastle.** { *; }

# Keep sora sdk classes
-keep class jp.co.soramitsu.sora.sdk.** { *; }
-keep class jp.co.soramitsu.shared_utils.** { *; }
-keep class net.jpountz.** { *; }
-keep class com.sun.jna.** { *; }

##--------------- Begin: Ed25519Sha3 ----------
-keep class jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
-keep class org.spongycastle.jcajce.provider.digest.SHA* { *; }
##--------------- End: Ed25519Sha3 ----------

# Google Drive and Signin
# Needed to keep generic types and @Key annotations accessed via reflection
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# Needed by google-http-client-android when linking against an older platform version

-dontwarn com.google.api.client.extensions.android.**

# Needed by google-api-client-android when linking against an older platform version

-dontwarn com.google.api.client.googleapis.extensions.android.**

# Needed by google-play-services when linking against an older platform version

-dontwarn com.google.android.gms.**
