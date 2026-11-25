package br.com.mrdosstos.visualizadoc.data

import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class OrdenadorTest {
    private val itens = listOf(
        ItemArquivo("1", "Ábaco.pdf", TipoDocumento.PDF, 300, 10),
        ItemArquivo("2", "zebra.docx", TipoDocumento.WORD, 100, 30),
        ItemArquivo("3", "banana.xlsx", TipoDocumento.PLANILHA, null, 20),
    )
    private val ptBR = Locale.forLanguageTag("pt-BR")

    private fun ordem(c: CriterioOrdem, crescente: Boolean) =
        Ordenador.ordenar(itens, Ordenacao(c, crescente), ptBR).map { it.uri }

    @Test fun dataDecrescente() = assertEquals(listOf("2", "3", "1"), ordem(CriterioOrdem.DATA, false))
    @Test fun nomeCrescenteIgnoraAcentoEMaiuscula() = assertEquals(listOf("1", "3", "2"), ordem(CriterioOrdem.NOME, true))
    @Test fun tamanhoCrescenteDesconhecidoPrimeiro() = assertEquals(listOf("3", "2", "1"), ordem(CriterioOrdem.TAMANHO, true))
}
