package br.com.mrdosstos.visualizadoc.documento

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream
import java.io.OutputStream

/**
 * Leitura de .doc (formato binário do Word 97–2003) e conversão para .docx.
 * O .doc é aberto só para leitura: gravar de volta nesse formato não é confiável.
 */
object WordAntigo {

    private val INSTRUCAO_DE_CAMPO = Regex("\u0013[^\u0014\u0015]*\u0014")
    private val CONTROLE = Regex("[\u0000-\u0008\u000C\u000E-\u001F]")

    fun ler(entrada: InputStream): List<Bloco> {
        val doc = HWPFDocument(entrada)
        try {
            val range = doc.range
            val estilos = doc.styleSheet
            return (0 until range.numParagraphs()).map { i ->
                val p = range.getParagraph(i)
                val trechos = (0 until p.numCharacterRuns()).map { j ->
                    val run = p.getCharacterRun(j)
                    Trecho(
                        texto = limpar(run.text()),
                        negrito = run.isBold,
                        italico = run.isItalic,
                        sublinhado = run.underlineCode != 0,
                        tachado = run.isStrikeThrough,
                    )
                }.filter { it.texto.isNotEmpty() }
                val nomeEstilo = runCatching { estilos.getStyleDescription(p.styleIndex.toInt())?.name }
                    .getOrNull()?.lowercase() ?: ""
                Bloco.Paragrafo(
                    indice = i,
                    trechos = trechos,
                    nivelTitulo = Regex("""^(heading|t[ií]tulo)\s*(\d)$""").find(nomeEstilo)
                        ?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                    alinhamento = when (p.justification.toInt()) {
                        1 -> Alinhamento.CENTRO
                        2 -> Alinhamento.DIREITA
                        3 -> Alinhamento.JUSTIFICADO
                        else -> Alinhamento.ESQUERDA
                    },
                    marcador = if (p.isInList) "•" else null,
                    nivelLista = if (p.isInList) p.ilvl.toInt() else 0,
                    editavel = false,
                )
            }
        } finally {
            doc.close()
        }
    }

    /** Monta um .docx com o texto e a formatação básica lidos do .doc. */
    fun converter(blocos: List<Bloco>, saida: OutputStream) {
        XWPFDocument().use { novo ->
            blocos.filterIsInstance<Bloco.Paragrafo>().forEach { b ->
                val p = novo.createParagraph()
                p.alignment = when (b.alinhamento) {
                    Alinhamento.CENTRO -> ParagraphAlignment.CENTER
                    Alinhamento.DIREITA -> ParagraphAlignment.RIGHT
                    Alinhamento.JUSTIFICADO -> ParagraphAlignment.BOTH
                    Alinhamento.ESQUERDA -> ParagraphAlignment.LEFT
                }
                val titulo = b.nivelTitulo != 0
                val tamanhoTitulo = when (b.nivelTitulo) { -1, 1 -> 20; 2 -> 16; else -> 14 }
                b.marcador?.let { p.createRun().setText("$it ") }
                b.trechos.forEach { t ->
                    t.texto.split('\n').forEachIndexed { k, parte ->
                        if (k > 0) p.createRun().addBreak()
                        if (parte.isNotEmpty()) {
                            p.createRun().apply {
                                setText(parte)
                                isBold = t.negrito || titulo
                                isItalic = t.italico
                                if (t.sublinhado) underline = UnderlinePatterns.SINGLE
                                isStrikeThrough = t.tachado
                                if (titulo) fontSize = tamanhoTitulo
                            }
                        }
                    }
                }
            }
            if (novo.paragraphs.isEmpty()) novo.createParagraph()
            novo.write(saida)
        }
    }

    /** Tira códigos de campo e caracteres de controle do texto do .doc. */
    fun limpar(texto: String): String =
        texto.replace(INSTRUCAO_DE_CAMPO, "")
            .replace('\u0007', '\t')   // fim de célula de tabela
            .replace('\u000B', '\n')   // quebra de linha manual
            .replace("\r", "")
            .replace(CONTROLE, "")
            .replace(Regex("[\u0013\u0014\u0015]"), "")
}
