// Empacota o Apache POI num único jar adaptado para o Android ("shadow jar").
//
// O Android não tem a API StAX (javax.xml.stream), usada pelo POI/XMLBeans.
// Aqui ela é incluída e "realocada" para org.apache.poi.javax.xml.stream, com o
// parser Aalto como implementação (ver PoiAndroid.configurar() no app).
//
// Baseado no projeto poi-on-android (Apache 2.0):
// https://github.com/centic9/poi-on-android

plugins {
    java
    alias(libs.plugins.shadow)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations.all {
    // Anotações só de compilação que o log4j-api traz; não são necessárias em runtime.
    exclude(group = "com.github.spotbugs", module = "spotbugs-annotations")
}

// Algumas classes de schema que o POI usa não vêm no poi-ooxml-lite;
// elas são copiadas do poi-ooxml-full (ver lista no shadowJar).
val ooxmlFull: Configuration by configurations.creating { isTransitive = false }

dependencies {
    implementation(libs.poi.ooxml)
    // Leitura de .doc (formato antigo do Word).
    implementation(libs.poi.scratchpad)
    implementation(libs.aalto.xml)
    implementation(libs.stax.api)
    implementation(libs.log4j.api)
    ooxmlFull(libs.poi.ooxml.full)
}

val classesExtrasDoFull = listOf(
    "com/microsoft/schemas/office/office/STOLEUpdateMode",
    "com/microsoft/schemas/office/office/STScreenSize",
    "com/microsoft/schemas/office/word/STHorizontalAnchor",
    "com/microsoft/schemas/office/word/STVerticalAnchor",
    "com/microsoft/schemas/office/word/STWrapSide",
    "com/microsoft/schemas/vml/STImageAspect",
    "com/microsoft/schemas/vml/STStrokeArrowLength",
    "com/microsoft/schemas/vml/STStrokeArrowWidth",
    "com/microsoft/schemas/vml/STStrokeEndCap",
    "com/microsoft/schemas/vml/STStrokeLineStyle",
    "org/openxmlformats/schemas/drawingml/x2006/chart/STPageSetupOrientation",
    "org/openxmlformats/schemas/drawingml/x2006/main/STEffectContainerType",
    "org/openxmlformats/schemas/drawingml/x2006/main/STPresetMaterialType",
    "org/openxmlformats/schemas/drawingml/x2006/wordprocessingDrawing/STAlignH",
    "org/openxmlformats/schemas/drawingml/x2006/wordprocessingDrawing/STAlignV",
    "org/openxmlformats/schemas/officeDocument/x2006/docPropsVTypes/STVectorBaseType",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STComments",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STDataValidationImeMode",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STOleUpdate",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STPhoneticAlignment",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STPivotAreaType",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STShowDataAs",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STSortBy",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STSortMethod",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STTableType",
    "org/openxmlformats/schemas/spreadsheetml/x2006/main/STTargetScreenSize",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STChapterSep",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STDropCap",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STPTabAlignment",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STPTabLeader",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STPTabRelativeTo",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STPageBorderZOrder",
    "org/openxmlformats/schemas/wordprocessingml/x2006/main/STZoom",
)

tasks.shadowJar {
    archiveClassifier.set("all")

    from({ zipTree(ooxmlFull.singleFile) }) {
        classesExtrasDoFull.forEach { include("$it.class") }
    }

    // O POI registra extratores/renderizadores via META-INF/services.
    duplicatesStrategy = DuplicatesStrategy.WARN
    mergeServiceFiles()
    // Estes apontariam para classes que não existem no Android;
    // as fábricas StAX são definidas por System.setProperty no app.
    exclude("META-INF/services/javax.xml.stream.*")

    exclude("repackage/**")
    exclude("module-info.class", "META-INF/versions/*/module-info.class")
    exclude("LICENSE.txt", "NOTICE.txt", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/maven/**")

    // Usadas só por Visio e outros formatos que o app não trata.
    dependencies {
        exclude(dependency("com.github.virtuald:curvesapi"))
        exclude(dependency("commons-codec:commons-codec"))
        exclude(dependency("org.apache.commons:commons-lang3"))
    }

    // Realoca as APIs javax.xml que o Android não tem (ou tem incompletas).
    relocate("javax.xml.namespace", "org.apache.poi.javax.xml.namespace")
    relocate("javax.xml.stream", "org.apache.poi.javax.xml.stream")
    relocate("javax.xml.XMLConstants", "org.apache.poi.javax.xml.XMLConstants")
}

tasks.jar { dependsOn(tasks.shadowJar) }
