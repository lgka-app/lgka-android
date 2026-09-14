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
