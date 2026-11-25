package br.com.mrdosstos.visualizadoc.documento

import br.com.mrdosstos.visualizadoc.planilha.PoiAndroid
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DocumentoWordTest {

    @Before fun configurarPoi() = PoiAndroid.configurar()

    /** Documento: "Olá " (negrito) + "mundo" + "!", um parágrafo centralizado e uma tabela 2x2. */
    private fun criar(): ByteArray {
        val doc = XWPFDocument()
        doc.createParagraph().apply {
            createRun().apply { setText("Olá "); isBold = true }
            createRun().setText("mundo")
            createRun().setText("!")
        }
        doc.createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            createRun().setText("Centro")
        }
        doc.createTable(2, 2).apply {
            getRow(0).getCell(0).text = "A1"
            getRow(0).getCell(1).text = "B1"
            getRow(1).getCell(0).text = "A2"
            getRow(1).getCell(1).text = "B2"
        }
        return ByteArrayOutputStream().also { doc.write(it); doc.close() }.toByteArray()
    }

    private fun abrir(bytes: ByteArray) = DocumentoWord.abrir(ByteArrayInputStream(bytes))

    private fun salvarEReabrir(d: DocumentoWord): DocumentoWord {
        val bytes = ByteArrayOutputStream().also { d.salvar(it) }.toByteArray()
        d.close()
        return abrir(bytes)
    }

    private fun paragrafos(d: DocumentoWord) = d.blocos().filterIsInstance<Bloco.Paragrafo>()

    @Test fun leBlocos() {
        abrir(criar()).use { d ->
            val blocos = d.blocos()
            val p0 = blocos[0] as Bloco.Paragrafo
            assertEquals("Olá mundo!", p0.texto)
            assertTrue(p0.trechos[0].negrito)
            assertFalse(p0.trechos[1].negrito)
            assertEquals(Alinhamento.CENTRO, (blocos[1] as Bloco.Paragrafo).alinhamento)
            val tabela = blocos[2] as Bloco.Tabela
            assertEquals(listOf(listOf("A1", "B1"), listOf("A2", "B2")), tabela.linhas)
        }
    }

    @Test fun editarMantemFormatacao() {
        val d = abrir(criar())
        d.editarTexto(0, "Olá grande mundo!")
        abrir(ByteArrayOutputStream().also { d.salvar(it) }.toByteArray()).use { r ->
            val p0 = paragrafos(r)[0]
            assertEquals("Olá grande mundo!", p0.texto)
            // O texto digitado logo após "Olá " herda o negrito dele.
            assertEquals(Trecho("Olá grande ", negrito = true), p0.trechos[0])
            assertEquals("mundo", p0.trechos[1].texto)
            assertFalse(p0.trechos[1].negrito)
        }
        d.close()
    }

    @Test fun alternarNegritoNoParagrafoInteiro() {
        val d = abrir(criar())
        assertFalse(d.formatoAtivo(0, Formato.NEGRITO))
        d.alternarFormato(0, Formato.NEGRITO)
        assertTrue(d.formatoAtivo(0, Formato.NEGRITO))
        d.alternarFormato(0, Formato.NEGRITO)
        assertFalse(paragrafos(d)[0].trechos.any { it.negrito })
        d.close()
    }

    @Test fun inserirERemoverParagrafo() {
        var d = abrir(criar())
        val novo = d.inserirParagrafoDepois(0)
        d.editarTexto(novo, "Inserido")
        d = salvarEReabrir(d)
        assertEquals(listOf("Olá mundo!", "Inserido", "Centro"), paragrafos(d).map { it.texto })
        d.removerParagrafo(1)
        assertEquals(listOf("Olá mundo!", "Centro"), paragrafos(d).map { it.texto })
        d.close()
    }

    @Test fun editarCelulaDaTabela() {
        var d = abrir(criar())
        val tabela = d.blocos().filterIsInstance<Bloco.Tabela>().first()
        d.editarCelula(tabela.indice, 1, 0, "Novo\nvalor")
        d = salvarEReabrir(d)
        val linhas = d.blocos().filterIsInstance<Bloco.Tabela>().first().linhas
        assertEquals("Novo\nvalor", linhas[1][0])
        assertEquals("B2", linhas[1][1])
        d.close()
    }

    @Test fun redistribuirTexto() {
        val trechos = listOf("Olá ", "mundo", "!")
        assertEquals(listOf("Olá ", "mdo", "!"), Edicao.redistribuir(trechos, "Olá mdo!"))
        assertEquals(listOf("Oi, ", "mundo", "!"), Edicao.redistribuir(trechos, "Oi, mundo!"))
        assertEquals(listOf("?", "", ""), Edicao.redistribuir(trechos, "?"))
        assertEquals(listOf("Olá ", "mundo", "!!!"), Edicao.redistribuir(trechos, "Olá mundo!!!"))
    }
}
