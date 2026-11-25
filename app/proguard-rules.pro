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

# O POI e o XMLBeans carregam muitas classes por reflexão, e otimizar dezenas de
# milhares de classes de schema deixa o R8 lento demais. Mantemos essas
# bibliotecas intactas; o R8 continua otimizando o código do próprio app.
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.** { *; }
-keep class org.w3.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.apache.logging.log4j.** { *; }
-keep class com.zaxxer.sparsebits.** { *; }

# Fábricas StAX definidas por System.setProperty (ver PoiAndroid).
-keep class com.fasterxml.aalto.** { *; }
-keep class org.codehaus.stax2.** { *; }
