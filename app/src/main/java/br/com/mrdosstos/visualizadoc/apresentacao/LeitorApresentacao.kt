package br.com.mrdosstos.visualizadoc.apresentacao

import org.apache.poi.hslf.usermodel.HSLFGroupShape
import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextParagraph
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.InputStream

/** Conteúdo de um slide, na ordem em que os elementos aparecem no arquivo. */
data class Slide(
    val numero: Int,
    val titulo: String?,
    val elementos: List<ElementoSlide>,
    val notas: String?,
)

sealed interface ElementoSlide {
    /** Parágrafo de texto; [nivel] é o recuo do tópico (0 = principal). */
    data class Texto(val texto: String, val topico: Boolean, val nivel: Int) : ElementoSlide
    data class Tabela(val linhas: List<List<String>>) : ElementoSlide
    class Imagem(val dados: ByteArray) : ElementoSlide
}

/**
 * Lê apresentações .pptx (XSLF) e .ppt (HSLF) como conteúdo: títulos, textos,
 * tabelas, imagens e notas. Não reproduz a posição exata dos elementos (o desenho
 * fiel de slides depende de bibliotecas gráficas que o Android não tem).
 */
object LeitorApresentacao {

    fun ler(entrada: InputStream, nomeArquivo: String): List<Slide> =
        if (nomeArquivo.lowercase().endsWith(".ppt")) lerPpt(entrada) else lerPptx(entrada)

    // ---------------------------------------------------------------- .pptx

    private fun lerPptx(entrada: InputStream): List<Slide> = XMLSlideShow(entrada).use { show ->
        show.slides.mapIndexed { i, slide ->
            val titulo = runCatching { slide.title }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val elementos = mutableListOf<ElementoSlide>()
            slide.shapes.forEach { coletarXslf(it, titulo, elementos) }
            val notas = runCatching {
                slide.notes?.textParagraphs?.flatten()
                    ?.map { it.text.trim() }?.filter { it.isNotEmpty() && !it.all(Char::isDigit) }
                    ?.joinToString("\n")
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            Slide(i + 1, titulo, elementos, notas)
        }
    }

    private fun coletarXslf(forma: XSLFShape, titulo: String?, saida: MutableList<ElementoSlide>) {
        when (forma) {
            is XSLFGroupShape -> forma.shapes.forEach { coletarXslf(it, titulo, saida) }
            is XSLFTable -> saida += ElementoSlide.Tabela(
                forma.rows.map { linha -> linha.cells.map { it.text.trim() } }
            )
            is XSLFPictureShape -> forma.pictureData?.data?.let { saida += ElementoSlide.Imagem(it) }
            is XSLFTextShape -> forma.textParagraphs.forEach { p ->
                val texto = p.text.trim()
                if (texto.isNotEmpty() && texto != titulo) {
                    val topico = runCatching { p.isBullet }.getOrDefault(false)
                    saida += ElementoSlide.Texto(texto, topico, p.indentLevel.coerceAtLeast(0))
                }
            }
        }
    }

    // ----------------------------------------------------------------- .ppt

    private fun lerPpt(entrada: InputStream): List<Slide> = HSLFSlideShow(entrada).use { show ->
        show.slides.mapIndexed { i, slide ->
            val titulo = runCatching { slide.title }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val elementos = mutableListOf<ElementoSlide>()
            slide.shapes.forEach { coletarHslf(it, titulo, elementos) }
            val notas = runCatching {
                slide.notes?.textParagraphs?.joinToString("\n") { HSLFTextParagraph.getText(it).trim() }
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            Slide(i + 1, titulo, elementos, notas)
        }
    }

    private fun coletarHslf(forma: HSLFShape, titulo: String?, saida: MutableList<ElementoSlide>) {
        when (forma) {
            // HSLFTable é um tipo de grupo: precisa vir antes.
            is HSLFTable -> saida += ElementoSlide.Tabela(
                (0 until forma.numberOfRows).map { l ->
                    (0 until forma.numberOfColumns).map { c -> forma.getCell(l, c)?.text?.trim() ?: "" }
                }
            )
            is HSLFGroupShape -> forma.shapes.forEach { coletarHslf(it, titulo, saida) }
            is HSLFPictureShape -> forma.pictureData?.data?.let { saida += ElementoSlide.Imagem(it) }
            is HSLFTextShape -> forma.textParagraphs.forEach { p ->
                val texto = HSLFTextParagraph.getText(listOf(p)).trim()
                if (texto.isNotEmpty() && texto != titulo) {
                    val topico = runCatching { p.isBullet }.getOrDefault(false)
                    saida += ElementoSlide.Texto(texto, topico, p.indentLevel.coerceAtLeast(0))
                }
            }
        }
    }
}
