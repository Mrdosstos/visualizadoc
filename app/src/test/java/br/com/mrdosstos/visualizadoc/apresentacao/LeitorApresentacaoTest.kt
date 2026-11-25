package br.com.mrdosstos.visualizadoc.apresentacao

import br.com.mrdosstos.visualizadoc.planilha.PoiAndroid
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LeitorApresentacaoTest {

    @Before fun configurarPoi() = PoiAndroid.configurar()

    @Test fun lePptxComTextoETabela() {
        val bytes = XMLSlideShow().use { show ->
            val slide = show.createSlide()
            slide.createTextBox().setText("Primeiro ponto")
            slide.createTable(2, 2).apply {
                rows[0].cells[0].setText("A")
                rows[0].cells[1].setText("B")
                rows[1].cells[0].setText("1")
                rows[1].cells[1].setText("2")
            }
            show.createSlide().createTextBox().setText("Segundo slide")
            ByteArrayOutputStream().also { show.write(it) }.toByteArray()
        }
        val slides = LeitorApresentacao.ler(ByteArrayInputStream(bytes), "aula.pptx")
        assertEquals(2, slides.size)
        val textos = slides[0].elementos.filterIsInstance<ElementoSlide.Texto>().map { it.texto }
        assertEquals(listOf("Primeiro ponto"), textos)
        val tabela = slides[0].elementos.filterIsInstance<ElementoSlide.Tabela>().single()
        assertEquals(listOf(listOf("A", "B"), listOf("1", "2")), tabela.linhas)
        assertEquals(2, slides[1].numero)
    }

    @Test fun lePptAntigo() {
        val bytes = HSLFSlideShow().use { show ->
            show.createSlide().createTextBox().setText("Olá do PPT")
            ByteArrayOutputStream().also { show.write(it) }.toByteArray()
        }
        val slides = LeitorApresentacao.ler(ByteArrayInputStream(bytes), "antigo.ppt")
        assertEquals(1, slides.size)
        assertTrue(slides[0].elementos.filterIsInstance<ElementoSlide.Texto>().any { it.texto == "Olá do PPT" })
    }
}
