package br.com.mrdosstos.visualizadoc.documento

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFSDT
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** Trecho de texto com a mesma formatação (um "run" do Word). */
data class Trecho(
    val texto: String,
    val negrito: Boolean = false,
    val italico: Boolean = false,
    val sublinhado: Boolean = false,
    val tachado: Boolean = false,
)

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA, JUSTIFICADO }

enum class Formato { NEGRITO, ITALICO, SUBLINHADO }

/** O que a tela desenha. [indice] aponta para o elemento do corpo do documento. */
sealed interface Bloco {
    val chave: String

    data class Paragrafo(
        val indice: Int,
        val trechos: List<Trecho>,
        /** 0 = texto normal; 1..6 = título; -1 = "Título" do documento. */
        val nivelTitulo: Int,
        val alinhamento: Alinhamento,
        /** Marcador da lista ("•", "1.", "a."), ou null se não for item de lista. */
        val marcador: String?,
        val nivelLista: Int,
        val editavel: Boolean = true,
    ) : Bloco {
        override val chave get() = "p$indice"
        val texto: String get() = trechos.joinToString("") { it.texto }
    }

    data class Tabela(val indice: Int, val linhas: List<List<String>>) : Bloco {
        override val chave get() = "t$indice"
    }

    data class Imagem(val indice: Int, val ordem: Int, val dados: ByteArray, val descricao: String) : Bloco {
        override val chave get() = "i$indice-$ordem"
        override fun equals(other: Any?) = other is Imagem && other.chave == chave
        override fun hashCode() = chave.hashCode()
    }
}

/**
 * Documento .docx aberto em memória, sobre o Apache POI (XWPF).
 *
 * As edições mexem só no que mudou: o resto do arquivo (estilos, cabeçalhos,
 * rodapés, campos, comentários) é mantido como estava.
 * Não é thread-safe.
 */
class DocumentoWord private constructor(private val doc: XWPFDocument) : Closeable {

    /** Lê o corpo do documento como blocos para a tela. */
    fun blocos(): List<Bloco> {
        val blocos = mutableListOf<Bloco>()
        val contadores = mutableMapOf<String, IntArray>()
        doc.bodyElements.forEachIndexed { indice, elemento ->
            when (elemento) {
                is XWPFParagraph -> {
                    val texto = trechosDe(elemento)
                    val imagens = imagensDe(elemento)
                    if (texto.isNotEmpty() || imagens.isEmpty()) {
                        blocos += Bloco.Paragrafo(
                            indice = indice,
                            trechos = texto,
                            nivelTitulo = nivelTitulo(elemento),
                            alinhamento = alinhamento(elemento),
                            marcador = marcador(elemento, contadores),
                            nivelLista = elemento.numIlvl?.toInt() ?: 0,
                        )
                    }
                    imagens.forEachIndexed { ordem, (dados, descricao) ->
                        blocos += Bloco.Imagem(indice, ordem, dados, descricao)
                    }
                }
                is XWPFTable -> blocos += Bloco.Tabela(
                    indice,
                    elemento.rows.map { linha -> linha.tableCells.map { it.text.trimEnd('\n') } },
                )
                is XWPFSDT -> blocos += Bloco.Paragrafo(
                    indice, listOf(Trecho(elemento.content.text)), 0, Alinhamento.ESQUERDA,
                    null, 0, editavel = false,
                )
            }
        }
        return blocos
    }

    /** Troca o texto do parágrafo, mantendo a formatação dos trechos que não mudaram. */
    fun editarTexto(indice: Int, novoTexto: String) {
        aplicarTexto(paragrafo(indice), novoTexto)
    }

    /** Liga ou desliga a formatação no parágrafo inteiro. */
    fun alternarFormato(indice: Int, formato: Formato) {
        val runs = runsDeTexto(paragrafo(indice))
        if (runs.isEmpty()) return
        val todos = runs.all { temFormato(it, formato) }
        runs.forEach { run ->
            when (formato) {
                Formato.NEGRITO -> run.isBold = !todos
                Formato.ITALICO -> run.isItalic = !todos
                Formato.SUBLINHADO -> run.underline = if (todos) UnderlinePatterns.NONE else UnderlinePatterns.SINGLE
            }
        }
    }

    fun formatoAtivo(indice: Int, formato: Formato): Boolean {
        val runs = runsDeTexto(paragrafo(indice))
        return runs.isNotEmpty() && runs.all { temFormato(it, formato) }
    }

    /**
     * Define o parágrafo como título (1..3) ou texto normal (0), usando os
     * estilos "heading N" do próprio documento. Se o documento não os tiver,
     * aplica negrito e tamanho de fonte como aproximação.
     */
    fun definirTitulo(indice: Int, nivel: Int) {
        val p = paragrafo(indice)
        val estilos = doc.styles
        if (nivel == 0) {
            val normal = estilos?.getStyleWithName("Normal")?.styleId
            if (normal != null) {
                p.style = normal
            } else {
                p.ctp.pPr?.let { if (it.isSetPStyle) it.unsetPStyle() }
            }
            return
        }
        val estilo = estilos?.getStyleWithName("heading $nivel")
        if (estilo != null) {
            p.style = estilo.styleId
        } else {
            runsDeTexto(p).forEach {
                it.isBold = true
                it.fontSize = when (nivel) { 1 -> 20; 2 -> 16; else -> 14 }
            }
        }
    }

    /** Insere um parágrafo vazio depois do indicado e devolve o índice dele. */
    fun inserirParagrafoDepois(indice: Int): Int {
        val atual = paragrafo(indice)
        val cursor = atual.ctp.newCursor()
        cursor.toEndToken()
        cursor.toNextToken()
        val novo = doc.insertNewParagraph(cursor)
            ?: throw IllegalStateException("Não foi possível inserir o parágrafo")
        cursor.close()
        // Continua a lista, se for o caso; título não "vaza" para o parágrafo novo.
        if (atual.numID != null) {
            novo.numID = atual.numID
            atual.numIlvl?.let { novo.setNumILvl(it) }
        }
        if (nivelTitulo(atual) == 0) novo.style = atual.style
        return doc.bodyElements.indexOf(novo)
    }

    fun removerParagrafo(indice: Int) {
        val p = paragrafo(indice)
        doc.removeBodyElement(doc.bodyElements.indexOf(p))
    }

    fun editarCelula(indiceTabela: Int, linha: Int, coluna: Int, texto: String) {
        val tabela = doc.bodyElements.getOrNull(indiceTabela) as? XWPFTable
            ?: throw IllegalArgumentException("Tabela não encontrada")
        val celula = tabela.getRow(linha)?.getCell(coluna)
            ?: throw IllegalArgumentException("Célula não encontrada")
        val linhasTexto = texto.split('\n')
        while (celula.paragraphs.size < linhasTexto.size) celula.addParagraph()
        while (celula.paragraphs.size > linhasTexto.size) celula.removeParagraph(celula.paragraphs.size - 1)
        celula.paragraphs.forEachIndexed { i, p -> aplicarTexto(p, linhasTexto[i]) }
    }

    fun salvar(saida: OutputStream) = doc.write(saida)

    override fun close() = doc.close()

    // ---------------------------------------------------------------------

    private fun paragrafo(indice: Int): XWPFParagraph =
        doc.bodyElements.getOrNull(indice) as? XWPFParagraph
            ?: throw IllegalArgumentException("Parágrafo não encontrado")

    /** Runs que têm texto (<w:t>); imagens, quebras e tabulações ficam de fora. */
    private fun runsDeTexto(p: XWPFParagraph): List<XWPFRun> =
        p.runs.filter { it.ctr.sizeOfTArray() > 0 }

    private fun textoDoRun(run: XWPFRun): String =
        (0 until run.ctr.sizeOfTArray()).joinToString("") { run.ctr.getTArray(it).stringValue ?: "" }

    private fun trechosDe(p: XWPFParagraph): List<Trecho> =
        runsDeTexto(p).map { run ->
            Trecho(
                texto = textoDoRun(run),
                negrito = run.isBold,
                italico = run.isItalic,
                sublinhado = run.underline != UnderlinePatterns.NONE,
                tachado = run.isStrikeThrough,
            )
        }.filter { it.texto.isNotEmpty() }

    private fun imagensDe(p: XWPFParagraph): List<Pair<ByteArray, String>> =
        p.runs.flatMap { run ->
            run.embeddedPictures.mapNotNull { foto ->
                val dados = foto.pictureData?.data ?: return@mapNotNull null
                dados to (foto.description ?: "")
            }
        }

    private fun temFormato(run: XWPFRun, formato: Formato) = when (formato) {
        Formato.NEGRITO -> run.isBold
        Formato.ITALICO -> run.isItalic
        Formato.SUBLINHADO -> run.underline != UnderlinePatterns.NONE
    }

    /**
     * Aplica o novo texto preservando a formatação: o começo e o fim que não
     * mudaram ficam nos runs originais, e o texto digitado entra no run onde a
     * edição começou (herdando a formatação dele).
     */
    private fun aplicarTexto(p: XWPFParagraph, novo: String) {
        val runs = runsDeTexto(p)
        if (runs.isEmpty()) {
            if (novo.isNotEmpty()) p.createRun().setText(novo)
            return
        }
        val textos = runs.map { textoDoRun(it) }
        val antigo = textos.joinToString("")
        if (antigo == novo) return

        val novosTextos = Edicao.redistribuir(textos, novo)
        runs.forEachIndexed { i, run ->
            if (novosTextos[i] != textos[i]) {
                while (run.ctr.sizeOfTArray() > 0) run.ctr.removeT(0)
                run.setText(novosTextos[i])
            }
        }
    }

    private fun nivelTitulo(p: XWPFParagraph): Int {
        val idEstilo = p.style ?: return 0
        val nome = (doc.styles?.getStyle(idEstilo)?.name ?: idEstilo).lowercase()
        if (nome == "title") return -1
        val numero = Regex("""^(heading|t[ií]tulo)\s*(\d)$""").find(nome)?.groupValues?.get(2)
        return numero?.toIntOrNull() ?: 0
    }

    private fun alinhamento(p: XWPFParagraph) = when (p.alignment) {
        ParagraphAlignment.CENTER -> Alinhamento.CENTRO
        ParagraphAlignment.RIGHT -> Alinhamento.DIREITA
        ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> Alinhamento.JUSTIFICADO
        else -> Alinhamento.ESQUERDA
    }

    /** Numeração de listas: um contador por lista e por nível. */
    private fun marcador(p: XWPFParagraph, contadores: MutableMap<String, IntArray>): String? {
        val numId = p.numID ?: return null
        val nivel = (p.numIlvl?.toInt() ?: 0).coerceIn(0, 8)
        val formato = runCatching { p.numFmt }.getOrNull() ?: "bullet"
        val contagem = contadores.getOrPut(numId.toString()) { IntArray(9) }
        contagem[nivel]++
        for (i in nivel + 1 until 9) contagem[i] = 0
        val n = contagem[nivel]
        return when (formato) {
            "decimal" -> "$n."
            "lowerLetter" -> "${'a' + (n - 1) % 26})"
            "upperLetter" -> "${'A' + (n - 1) % 26}."
            "lowerRoman" -> romano(n).lowercase() + "."
            "upperRoman" -> romano(n) + "."
            else -> if (nivel % 2 == 0) "•" else "◦"
        }
    }

    private fun romano(n: Int): String {
        val valores = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var resto = n
        return buildString {
            for ((v, s) in valores) while (resto >= v) { append(s); resto -= v }
        }
    }

    companion object {
        fun abrir(entrada: InputStream): DocumentoWord = DocumentoWord(XWPFDocument(entrada))

        /** Usado nos testes e, no futuro, para "Novo documento". */
        fun novo(): DocumentoWord = DocumentoWord(XWPFDocument())

        internal fun deXwpf(doc: XWPFDocument) = DocumentoWord(doc)
    }
}

/** Regra pura (testável) de como redistribuir um texto editado entre os trechos. */
object Edicao {

    fun redistribuir(textos: List<String>, novo: String): List<String> {
        val antigo = textos.joinToString("")
        val maxPrefixo = minOf(antigo.length, novo.length)
        var prefixo = 0
        while (prefixo < maxPrefixo && antigo[prefixo] == novo[prefixo]) prefixo++
        var sufixo = 0
        while (sufixo < maxPrefixo - prefixo &&
            antigo[antigo.length - 1 - sufixo] == novo[novo.length - 1 - sufixo]
        ) sufixo++

        val inserido = novo.substring(prefixo, novo.length - sufixo)
        val fimRemovido = antigo.length - sufixo

        // Trecho que recebe o texto digitado: o que contém o caractere antes do
        // ponto de edição (continua a formatação de quem está digitando).
        var alvo = 0
        var inicio = 0
        for ((i, t) in textos.withIndex()) {
            val fim = inicio + t.length
            if (prefixo in (inicio + 1)..fim || (prefixo == 0 && i == 0)) { alvo = i; break }
            inicio = fim
        }

        val resultado = mutableListOf<String>()
        inicio = 0
        for ((i, t) in textos.withIndex()) {
            val fim = inicio + t.length
            val antes = t.substring(0, (prefixo - inicio).coerceIn(0, t.length))
            val depois = t.substring((fimRemovido - inicio).coerceIn(0, t.length))
            resultado += if (i == alvo) antes + inserido + depois else antes + depois
            inicio = fim
        }
        return resultado
    }
}
