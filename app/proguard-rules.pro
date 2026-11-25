# Regras do R8 para o VisualizaDoc.
#
# Modo conservador: o R8 só REMOVE o que não é usado. Otimizar e renomear
# classes travava o build com o Apache POI e costuma quebrar bibliotecas que
# usam reflexão; por isso ficam desligados.
-dontoptimize
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ---------- Classes referenciadas que não existem no Android ----------
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
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.**

# ---------- Carregado por reflexão: manter inteiro ----------
# XMLBeans e schemas do Office (POI carrega os tipos pelo nome).
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.poi.schemas.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.** { *; }
-keep class org.w3.** { *; }
# Funções de fórmula do POI (registradas por nome).
-keep class org.apache.poi.ss.formula.** { *; }
# Registros/relacionamentos do POI usados via fábricas.
-keep class org.apache.poi.openxml4j.** { *; }
-keep class org.apache.poi.ooxml.** { *; }
-keep class org.apache.poi.hssf.record.** { *; }
-keep class org.apache.poi.hslf.record.** { *; }
-keep class org.apache.poi.hwpf.** { *; }
# Log e compactação usados pelo POI.
-keep class org.apache.logging.log4j.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.collections4.** { *; }
-keep class com.zaxxer.sparsebits.** { *; }
# Fábricas StAX definidas por System.setProperty (ver PoiAndroid).
-keep class com.fasterxml.aalto.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class org.apache.poi.javax.xml.** { *; }
# PdfBox-Android (fontes e recursos carregados por nome).
-keep class com.tom_roush.** { *; }
