package br.com.mrdosstos.visualizadoc.planilha

/**
 * O Android não tem a API StAX. No jar do POI (módulo :poishadow) ela foi
 * realocada para org.apache.poi.javax.xml.stream; aqui apontamos as fábricas
 * para o parser Aalto, que vai junto no jar.
 */
object PoiAndroid {
    private const val PREFIXO = "org.apache.poi.javax.xml.stream"

    fun configurar() {
        System.setProperty("$PREFIXO.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("$PREFIXO.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("$PREFIXO.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    }
}
