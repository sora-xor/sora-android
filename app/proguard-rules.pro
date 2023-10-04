# General
-keepattributes SourceFile,LineNumberTable,*Annotation*,EnclosingMethod,Signature,Exceptions,InnerClasses

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile
#-repackageclasses
#-allowaccessmodification

#Firebase Crashlytics
-keep,includedescriptorclasses public class * extends java.lang.Exception

###########
-keep public class * extends jp.co.soramitsu.common.util.ParseModel {
    <fields>;
    <methods>;
 }

 # This is generated automatically by the Android Gradle plugin.
 -dontwarn build.IgnoreJava8API
 -dontwarn java.awt.Component
 -dontwarn java.awt.GraphicsEnvironment
 -dontwarn java.awt.HeadlessException
 -dontwarn java.awt.Window
 -dontwarn java.beans.ConstructorProperties
 -dontwarn java.beans.Transient
 -dontwarn java.lang.management.ManagementFactory
 -dontwarn java.lang.management.RuntimeMXBean
 -dontwarn java.lang.management.ThreadMXBean
 -dontwarn javax.servlet.ServletContextListener
 -dontwarn lombok.NonNull
 -dontwarn org.apache.avalon.framework.logger.Logger
 -dontwarn org.apache.log.Hierarchy
 -dontwarn org.apache.log.Logger
 -dontwarn org.apache.log4j.Level
 -dontwarn org.apache.log4j.Logger
 -dontwarn org.apache.log4j.Priority
 -dontwarn org.apache.xml.resolver.Catalog
 -dontwarn org.apache.xml.resolver.CatalogManager
 -dontwarn org.apache.xml.resolver.readers.CatalogReader
 -dontwarn org.apache.xml.resolver.readers.SAXCatalogReader
 -dontwarn org.ietf.jgss.GSSContext
 -dontwarn org.ietf.jgss.GSSCredential
 -dontwarn org.ietf.jgss.GSSException
 -dontwarn org.ietf.jgss.GSSManager
 -dontwarn org.ietf.jgss.GSSName
 -dontwarn org.ietf.jgss.Oid
 -dontwarn org.slf4j.impl.StaticLoggerBinder
 -dontwarn org.slf4j.impl.StaticMDCBinder
 -dontwarn org.slf4j.impl.StaticMarkerBinder
 -dontwarn org.w3c.dom.events.DocumentEvent
 -dontwarn org.w3c.dom.events.Event
 -dontwarn org.w3c.dom.events.EventException
 -dontwarn org.w3c.dom.events.EventListener
 -dontwarn org.w3c.dom.events.EventTarget
 -dontwarn org.w3c.dom.events.MouseEvent
 -dontwarn org.w3c.dom.events.MutationEvent
 -dontwarn org.w3c.dom.events.UIEvent
 -dontwarn org.w3c.dom.html.HTMLAnchorElement
 -dontwarn org.w3c.dom.html.HTMLAppletElement
 -dontwarn org.w3c.dom.html.HTMLAreaElement
 -dontwarn org.w3c.dom.html.HTMLBRElement
 -dontwarn org.w3c.dom.html.HTMLBaseElement
 -dontwarn org.w3c.dom.html.HTMLBaseFontElement
 -dontwarn org.w3c.dom.html.HTMLBodyElement
 -dontwarn org.w3c.dom.html.HTMLButtonElement
 -dontwarn org.w3c.dom.html.HTMLCollection
 -dontwarn org.w3c.dom.html.HTMLDListElement
 -dontwarn org.w3c.dom.html.HTMLDirectoryElement
 -dontwarn org.w3c.dom.html.HTMLDivElement
 -dontwarn org.w3c.dom.html.HTMLDocument
 -dontwarn org.w3c.dom.html.HTMLElement
 -dontwarn org.w3c.dom.html.HTMLFieldSetElement
 -dontwarn org.w3c.dom.html.HTMLFontElement
 -dontwarn org.w3c.dom.html.HTMLFormElement
 -dontwarn org.w3c.dom.html.HTMLFrameElement
 -dontwarn org.w3c.dom.html.HTMLFrameSetElement
 -dontwarn org.w3c.dom.html.HTMLHRElement
 -dontwarn org.w3c.dom.html.HTMLHeadElement
 -dontwarn org.w3c.dom.html.HTMLHeadingElement
 -dontwarn org.w3c.dom.html.HTMLHtmlElement
 -dontwarn org.w3c.dom.html.HTMLIFrameElement
 -dontwarn org.w3c.dom.html.HTMLImageElement
 -dontwarn org.w3c.dom.html.HTMLInputElement
 -dontwarn org.w3c.dom.html.HTMLIsIndexElement
 -dontwarn org.w3c.dom.html.HTMLLIElement
 -dontwarn org.w3c.dom.html.HTMLLabelElement
 -dontwarn org.w3c.dom.html.HTMLLegendElement
 -dontwarn org.w3c.dom.html.HTMLLinkElement
 -dontwarn org.w3c.dom.html.HTMLMapElement
 -dontwarn org.w3c.dom.html.HTMLMenuElement
 -dontwarn org.w3c.dom.html.HTMLMetaElement
 -dontwarn org.w3c.dom.html.HTMLModElement
 -dontwarn org.w3c.dom.html.HTMLOListElement
 -dontwarn org.w3c.dom.html.HTMLObjectElement
 -dontwarn org.w3c.dom.html.HTMLOptGroupElement
 -dontwarn org.w3c.dom.html.HTMLOptionElement
 -dontwarn org.w3c.dom.html.HTMLParagraphElement
 -dontwarn org.w3c.dom.html.HTMLParamElement
 -dontwarn org.w3c.dom.html.HTMLPreElement
 -dontwarn org.w3c.dom.html.HTMLQuoteElement
 -dontwarn org.w3c.dom.html.HTMLScriptElement
 -dontwarn org.w3c.dom.html.HTMLSelectElement
 -dontwarn org.w3c.dom.html.HTMLStyleElement
 -dontwarn org.w3c.dom.html.HTMLTableCaptionElement
 -dontwarn org.w3c.dom.html.HTMLTableCellElement
 -dontwarn org.w3c.dom.html.HTMLTableColElement
 -dontwarn org.w3c.dom.html.HTMLTableElement
 -dontwarn org.w3c.dom.html.HTMLTableRowElement
 -dontwarn org.w3c.dom.html.HTMLTableSectionElement
 -dontwarn org.w3c.dom.html.HTMLTextAreaElement
 -dontwarn org.w3c.dom.html.HTMLTitleElement
 -dontwarn org.w3c.dom.html.HTMLUListElement
 -dontwarn org.w3c.dom.ls.LSSerializerFilter
 -dontwarn org.w3c.dom.ranges.DocumentRange
 -dontwarn org.w3c.dom.ranges.Range
 -dontwarn org.w3c.dom.ranges.RangeException
 -dontwarn org.w3c.dom.traversal.DocumentTraversal
 -dontwarn org.w3c.dom.traversal.NodeFilter
 -dontwarn org.w3c.dom.traversal.NodeIterator
 -dontwarn org.w3c.dom.traversal.TreeWalker
 -dontwarn org.web3j.abi.datatypes.generated.AbiTypes
 -dontwarn org.webrtc.Dav1dDecoder
 -dontwarn sun.security.x509.X509Key

-dontwarn java.lang.invoke.StringConcatFactory

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
-keep class jp.co.soramitsu.xsubstrate.** { *; }
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
