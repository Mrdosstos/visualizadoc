package br.com.mrdosstos.visualizadoc.documento

import br.com.mrdosstos.visualizadoc.planilha.FormatoPlanilha
import br.com.mrdosstos.visualizadoc.planilha.Planilha
import br.com.mrdosstos.visualizadoc.planilha.PoiAndroid
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NovosArquivosTest {

    @Before fun configurarPoi() = PoiAndroid.configurar()

    @Test fun docxEmBrancoTemUmParagrafoEditavel() {
        val bytes = ByteArrayOutputStream().also { DocumentoWord.escreverEmBranco(it) }.toByteArray()
        DocumentoWord.abrir(ByteArrayInputStream(bytes)).use { d ->
            val p = d.blocos().single() as Bloco.Paragrafo
            assertEquals("", p.texto)
            d.editarTexto(p.indice, "Primeira linha")
            assertEquals("Primeira linha", (d.blocos().single() as Bloco.Paragrafo).texto)
        }
    }

    @Test fun xlsxEmBrancoTemUmaAba() {
        val bytes = ByteArrayOutputStream().also { Planilha.escreverEmBranco(it) }.toByteArray()
        Planilha.abrir(ByteArrayInputStream(bytes), FormatoPlanilha.XLSX).use { p ->
            assertEquals(listOf("Planilha1"), p.nomesAbas)
            assertEquals(0 to 0, p.dimensoes(0))
        }
    }

    @Test fun limparTextoDoDocAntigo() {
        val bruto = "Veja \u0013 HYPERLINK \"http://x\" \u0014o site\u0015 agora\r"
        assertEquals("Veja o site agora", WordAntigo.limpar(bruto))
        assertEquals("A\tB", WordAntigo.limpar("A\u0007B"))
        assertEquals("linha1\nlinha2", WordAntigo.limpar("linha1\u000Blinha2"))
    }

    @Test fun converterBlocosParaDocx() {
        val blocos = listOf(
            Bloco.Paragrafo(0, listOf(Trecho("Título", negrito = true)), 1, Alinhamento.CENTRO, null, 0, false),
            Bloco.Paragrafo(1, listOf(Trecho("Texto "), Trecho("itálico", italico = true)), 0, Alinhamento.ESQUERDA, null, 0, false),
        )
        val bytes = ByteArrayOutputStream().also { WordAntigo.converter(blocos, it) }.toByteArray()
        DocumentoWord.abrir(ByteArrayInputStream(bytes)).use { d ->
            val ps = d.blocos().filterIsInstance<Bloco.Paragrafo>()
            assertEquals(listOf("Título", "Texto itálico"), ps.map { it.texto })
            assertEquals(Alinhamento.CENTRO, ps[0].alinhamento)
            assertEquals(true, ps[1].trechos.last().italico)
        }
    }
}
