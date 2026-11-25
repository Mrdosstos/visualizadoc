package br.com.mrdosstos.visualizadoc.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgrupamentoTest {

    /** Caracteres de 6 pt de largura, fonte 12, numa linha de base [base]. */
    private fun palavra(texto: String, x: Float, base: Float, negrito: Boolean = false) =
        texto.mapIndexed { i, c ->
            Glifo(c.toString(), x + i * 6f, base, 6f, 9f, 12f, 3f, negrito)
        }

    @Test fun juntaPalavrasDaMesmaLinhaComEspaco() {
        val glifos = palavra("Olá", 50f, 100f) + palavra("mundo", 50f + 3 * 6f + 4f, 100.5f)
        val trechos = Agrupamento.agrupar(0, glifos)
        assertEquals(1, trechos.size)
        assertEquals("Olá mundo", trechos[0].texto)
        assertEquals(50f, trechos[0].x)
    }

    @Test fun separaLinhas() {
        val glifos = palavra("Linha2", 50f, 120f) + palavra("Linha1", 50f, 100f)
        val trechos = Agrupamento.agrupar(0, glifos)
        assertEquals(listOf("Linha1", "Linha2"), trechos.map { it.texto })
    }

    @Test fun separaColunasDistantes() {
        val glifos = palavra("Nome", 50f, 100f) + palavra("Valor", 300f, 100f)
        val trechos = Agrupamento.agrupar(0, glifos)
        assertEquals(listOf("Nome", "Valor"), trechos.map { it.texto })
    }

    @Test fun negritoPelaMaioria() {
        val trechos = Agrupamento.agrupar(0, palavra("Titulo", 10f, 20f, negrito = true))
        assertTrue(trechos[0].negrito)
    }

    @Test fun normalizaParaBusca() {
        assertEquals("acao sao paulo", PdfEditavel.normalizar("AÇÃO São Paulo"))
    }
}
