package br.com.mrdosstos.visualizadoc.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.text.Normalizer

/**
 * Um trecho de texto numa linha da página, em pontos PDF com origem no canto
 * superior esquerdo da página (como na tela). [base] é a linha de base do texto.
 */
data class TrechoPdf(
    val pagina: Int,
    val texto: String,
    val x: Float,
    val base: Float,
    val largura: Float,
    val altura: Float,
    val tamanhoFonte: Float,
    val negrito: Boolean,
) {
    val topo: Float get() = base - altura
}

/** Resultado de busca: página e posição do trecho na lista daquela página. */
data class Ocorrencia(val pagina: Int, val trecho: Int)

/**
 * PDF aberto com a PdfBox para ler o texto e editar.
 *
 * A edição é "por cima": cobre o texto antigo com um retângulo branco e escreve o
 * novo no mesmo lugar e tamanho, em Helvetica. O texto antigo continua no arquivo,
 * por baixo. Não é thread-safe.
 */
class PdfEditavel private constructor(private val doc: PDDocument) : Closeable {

    val totalPaginas: Int get() = doc.numberOfPages

    /** Edição permitida pelo próprio arquivo (alguns PDFs proíbem alterações). */
    val podeEditar: Boolean get() = doc.currentAccessPermission.canModify()

    private val trechos = mutableMapOf<Int, MutableList<TrechoPdf>>()

    /** Trechos de texto da página (extraídos uma vez e atualizados a cada edição). */
    fun trechos(pagina: Int): List<TrechoPdf> = trechos.getOrPut(pagina) { extrair(pagina).toMutableList() }

    fun buscar(termo: String): List<Ocorrencia> {
        val alvo = normalizar(termo.trim())
        if (alvo.isEmpty()) return emptyList()
        return (0 until totalPaginas).flatMap { p ->
            trechos(p).withIndex().filter { normalizar(it.value.texto).contains(alvo) }.map { Ocorrencia(p, it.index) }
        }
    }

    /** Página sem rotação: só nelas a edição por cima fica no lugar certo. */
    fun paginaEditavel(pagina: Int): Boolean = doc.getPage(pagina).rotation % 360 == 0

    /** Substitui o texto do trecho. Retorna os caracteres que a fonte não tem (trocados por "?"). */
    fun substituir(pagina: Int, indice: Int, novoTexto: String): Set<Char> {
        require(podeEditar) { "Este PDF não permite alterações." }
        require(paginaEditavel(pagina)) { "Páginas giradas ainda não podem ser editadas." }
        val lista = trechos(pagina) as MutableList<TrechoPdf>
        val trecho = lista[indice]
        val page = doc.getPage(pagina)
        val caixa = page.cropBox
        val fonte: PDFont = if (trecho.negrito) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA

        val (texto, faltando) = textoSuportado(fonte, novoTexto)
        val tamanho = trecho.tamanhoFonte.coerceAtLeast(4f)
        val larguraNova = fonte.getStringWidth(texto) / 1000f * tamanho

        // Coordenadas PDF: origem embaixo à esquerda.
        val x = caixa.lowerLeftX + trecho.x
        val baseY = caixa.upperRightY - trecho.base
        val folga = tamanho * 0.25f

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setNonStrokingColor(1f, 1f, 1f)
            cs.addRect(
                x - 1f,
                baseY - folga,
                maxOf(trecho.largura, larguraNova) + 2f,
                trecho.altura + folga * 2,
            )
            cs.fill()
            if (texto.isNotEmpty()) {
                cs.setNonStrokingColor(0f, 0f, 0f)
                cs.beginText()
                cs.setFont(fonte, tamanho)
                cs.newLineAtOffset(x, baseY)
                cs.showText(texto)
                cs.endText()
            }
        }
        // A área coberta passa a ser a maior das duas, para uma nova edição cobrir tudo.
        lista[indice] = trecho.copy(texto = texto, largura = maxOf(trecho.largura, larguraNova))
        return faltando
    }

    /** Grava o PDF inteiro (com as edições). */
    fun salvar(saida: OutputStream) {
        prepararParaSalvar()
        doc.save(saida)
    }

    fun salvar(arquivo: File) {
        prepararParaSalvar()
        doc.save(arquivo)
    }

    /** PDF com senha só de "dono" (que permite edição): a PdfBox exige tirar a criptografia para gravar. */
    private fun prepararParaSalvar() {
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
    }

    override fun close() = doc.close()

    // ---------------------------------------------------------------------

    private fun extrair(pagina: Int): List<TrechoPdf> {
        if (!paginaEditavel(pagina)) return emptyList()
        val coletor = Coletor()
        coletor.sortByPosition = true
        coletor.startPage = pagina + 1
        coletor.endPage = pagina + 1
        coletor.getText(doc)
        return Agrupamento.agrupar(pagina, coletor.posicoes.map { it.paraGlifo() })
    }

    /** Recebe cada caractere posicionado que a PdfBox encontra na página. */
    private class Coletor : PDFTextStripper() {
        val posicoes = mutableListOf<TextPosition>()
        override fun processTextPosition(text: TextPosition) {
            if (text.dir == 0f) posicoes += text
        }
    }

    private fun TextPosition.paraGlifo() = Glifo(
        texto = unicode ?: "",
        x = xDirAdj,
        base = yDirAdj,
        largura = widthDirAdj,
        altura = maxOf(heightDir, fontSizeInPt * 0.7f),
        tamanhoFonte = fontSizeInPt,
        larguraEspaco = widthOfSpace,
        negrito = font?.name?.contains("bold", ignoreCase = true) == true,
    )

    private fun textoSuportado(fonte: PDFont, texto: String): Pair<String, Set<Char>> {
        val faltando = mutableSetOf<Char>()
        val resultado = buildString {
            texto.forEach { c ->
                val ok = runCatching { fonte.encode(c.toString()) }.isSuccess
                if (ok) append(c) else { faltando += c; append('?') }
            }
        }
        return resultado to faltando
    }

    companion object {
        /** Abre o arquivo usando disco temporário em vez de memória para PDFs grandes. */
        fun abrir(arquivo: File): PdfEditavel =
            PdfEditavel(PDDocument.load(arquivo, MemoryUsageSetting.setupTempFileOnly()))

        fun normalizar(texto: String): String =
            Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
    }
}

/** Caractere posicionado (independente da PdfBox, para poder testar o agrupamento). */
data class Glifo(
    val texto: String,
    val x: Float,
    val base: Float,
    val largura: Float,
    val altura: Float,
    val tamanhoFonte: Float,
    val larguraEspaco: Float,
    val negrito: Boolean,
)

/** Junta caracteres em linhas e as linhas em trechos (separando colunas). */
object Agrupamento {

    fun agrupar(pagina: Int, glifos: List<Glifo>): List<TrechoPdf> {
        val linhas = mutableListOf<MutableList<Glifo>>()
        for (g in glifos.filter { it.texto.isNotEmpty() }.sortedWith(compareBy({ it.base }, { it.x }))) {
            val linha = linhas.lastOrNull()
            val referencia = linha?.first()
            if (referencia != null && kotlin.math.abs(g.base - referencia.base) <= maxOf(referencia.altura, g.altura) * 0.5f) {
                linha += g
            } else {
                linhas += mutableListOf(g)
            }
        }

        val trechos = mutableListOf<TrechoPdf>()
        for (linha in linhas) {
            linha.sortBy { it.x }
            var atual = mutableListOf<Glifo>()
            for (g in linha) {
                val anterior = atual.lastOrNull()
                if (anterior != null) {
                    val espaco = g.x - (anterior.x + anterior.largura)
                    val limiteColuna = maxOf(anterior.larguraEspaco * 3f, anterior.tamanhoFonte * 1.5f)
                    if (espaco > limiteColuna) {
                        trechos += montar(pagina, atual)
                        atual = mutableListOf()
                    }
                }
                atual += g
            }
            if (atual.isNotEmpty()) trechos += montar(pagina, atual)
        }
        return trechos.filter { it.texto.isNotBlank() }
    }

    private fun montar(pagina: Int, glifos: List<Glifo>): TrechoPdf {
        val texto = buildString {
            glifos.forEachIndexed { i, g ->
                if (i > 0) {
                    val anterior = glifos[i - 1]
                    val espaco = g.x - (anterior.x + anterior.largura)
                    val limite = maxOf(anterior.larguraEspaco, anterior.tamanhoFonte * 0.2f) * 0.5f
                    if (espaco > limite && !endsWith(" ") && g.texto != " ") append(' ')
                }
                append(g.texto)
            }
        }.trim()
        val primeiro = glifos.first()
        val ultimo = glifos.last()
        return TrechoPdf(
            pagina = pagina,
            texto = texto,
            x = primeiro.x,
            base = glifos.map { it.base }.average().toFloat(),
            largura = (ultimo.x + ultimo.largura) - primeiro.x,
            altura = glifos.maxOf { it.altura },
            tamanhoFonte = glifos.maxOf { it.tamanhoFonte },
            negrito = glifos.count { it.negrito } * 2 > glifos.size,
        )
    }
}
