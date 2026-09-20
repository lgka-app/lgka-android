# kotlinx.serialization: keep the generated serializers of our API models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class lgka.api.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class lgka.api.**$$serializer { *; }
# OkHttp ships its own consumer rules; nothing else here uses reflection.

# pdfbox-android: the optional JPEG 2000 decoder is not bundled (Stufenplan PDFs are text only).
-dontwarn com.gemalto.jp2.JP2Decoder

# ML Kit's text recognition is wired up by Firebase ComponentDiscovery, which instantiates every
# ComponentRegistrar reflectively. ML Kit ships "-keep class * implements ComponentRegistrar", which
# keeps the classes but not their members, so R8 in full mode strips their no-arg constructors:
# discovery then registers nothing and TextRecognition.getClient() throws an NPE on first use
# (release only — the scan failed with the generic error on every device).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
    public java.util.List getComponents();
}
