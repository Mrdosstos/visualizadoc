package br.com.mrdosstos.visualizadoc.planilha

import br.com.mrdosstos.visualizadoc.documento.DocumentoWord
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Garante que o jar do POI (módulo :poishadow) traz tudo de que ele precisa em runtime. */
class DependenciasPoiTest {

    @Before fun configurarPoi() = PoiAndroid.configurar()

    @Test fun bibliotecasDeApoioPresentes() {
        // Usada pelo commons-compress ao ler campos extras do ZIP (falha da v0.7.0).
        Class.forName("org.apache.commons.lang3.ArrayUtils")
        Class.forName("org.apache.commons.codec.binary.Base64")
    }

    /** .docx cujas entradas do ZIP têm um "campo extra" desconhecido, como o que travou no celular. */
    @Test fun abreDocxComCampoExtraNoZip() {
        val original = ByteArrayOutputStream().also { DocumentoWord.escreverEmBranco(it) }.toByteArray()
        val comExtra = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(original)).use { entrada ->
            ZipOutputStream(comExtra).use { saida ->
                var e = entrada.nextEntry
                while (e != null) {
                    val nova = ZipEntry(e.name)
                    // Cabeçalho 0xCAFE (desconhecido), 4 bytes de dados.
                    nova.extra = byteArrayOf(0xFE.toByte(), 0xCA.toByte(), 4, 0, 1, 2, 3, 4)
                    saida.putNextEntry(nova)
                    entrada.copyTo(saida)
                    saida.closeEntry()
                    e = entrada.nextEntry
                }
            }
        }
        DocumentoWord.abrir(ByteArrayInputStream(comExtra.toByteArray())).use { d ->
            assertEquals(1, d.blocos().size)
        }
    }
}
