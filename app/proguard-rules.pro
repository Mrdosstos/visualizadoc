# Regras do R8 para o VisualizaDoc.

# ---------- Apache POI (baseado no poi-on-android, Apache 2.0) ----------
# O POI referencia classes que não existem no Android (java.awt, assinaturas
# digitais, Saxon, Batik...). Elas só seriam usadas em recursos que o app não chama.
-dontwarn org.apache.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.**
-dontwarn edu.umd.cs.findbugs.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.**
-dontwarn org.objectweb.asm.**
-dontwarn com.fasterxml.aalto.**
-dontwarn org.codehaus.stax2.**

-keeppackagenames org.apache.poi.ss.formula.function

# O XMLBeans carrega os tipos de schema por reflexão.
-keep,allowoptimization class org.apache.poi.schemas.** { *; }
-keep,allowoptimization class org.apache.xmlbeans.** { *; }
-keep,allowoptimization class org.openxmlformats.schemas.** { *; }
-keep,allowoptimization class com.microsoft.schemas.** { *; }
-keep,allowoptimization class org.apache.commons.compress.archivers.zip.** { *; }
-keep,allowoptimization,allowobfuscation class org.apache.logging.log4j.** { *; }

# Fábricas StAX definidas por System.setProperty (ver PoiAndroid).
-keep class com.fasterxml.aalto.stax.** { *; }
-keep class org.apache.poi.javax.xml.stream.** { *; }
