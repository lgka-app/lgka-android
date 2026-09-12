# PdfBox-Android resolves filters and fonts reflectively.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.**
-dontwarn java.awt.**
# Jsoup has no reflection; keep its select package warnings quiet under R8 full mode.
-dontwarn org.jsoup.**
