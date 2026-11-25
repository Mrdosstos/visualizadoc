package br.com.mrdosstos.visualizadoc.planilha

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Como o CSV original estava escrito, para salvar no mesmo formato. */
data class FormatoCsv(
    val separador: Char,
    val charset: Charset,
    val comBom: Boolean,
    val quebraLinha: String,
)

/** Leitura e escrita de CSV (RFC 4180), com detecção de separador e codificação. */
object Csv {

    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun ler(bytes: ByteArray): Pair<List<List<String>>, FormatoCsv> {
        val comBom = bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(BOM_UTF8)
        val conteudo = if (comBom) bytes.copyOfRange(3, bytes.size) else bytes

        // UTF-8 se for válido; senão, o padrão do Excel no Windows (Latin-1 estendido).
        val (texto, charset) = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(conteudo))
                .toString() to Charsets.UTF_8
        } catch (_: CharacterCodingException) {
            val windows = Charset.forName("windows-1252")
            String(conteudo, windows) to windows
        }

        val separador = detectarSeparador(texto)
        val quebra = if (texto.contains("\r\n")) "\r\n" else "\n"
        return analisar(texto, separador) to FormatoCsv(separador, charset, comBom, quebra)
    }

    /** Escolhe entre ; , e tab pelo que mais aparece na primeira linha (fora de aspas). */
    fun detectarSeparador(texto: String): Char {
        val contagem = mutableMapOf(';' to 0, ',' to 0, '\t' to 0)
        var entreAspas = false
        for (c in texto) {
            if (c == '"') entreAspas = !entreAspas
            else if (!entreAspas && (c == '\n' || c == '\r')) {
                if (contagem.values.any { it > 0 }) break
            } else if (!entreAspas && c in contagem) {
                contagem[c] = contagem.getValue(c) + 1
            }
        }
        return contagem.maxBy { it.value }.takeIf { it.value > 0 }?.key ?: ','
    }

    fun analisar(texto: String, separador: Char): List<List<String>> {
        val linhas = mutableListOf<List<String>>()
        var linha = mutableListOf<String>()
        val campo = StringBuilder()
        var entreAspas = false
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (entreAspas) {
                if (c == '"') {
                    if (i + 1 < texto.length && texto[i + 1] == '"') {
                        campo.append('"'); i++
                    } else {
                        entreAspas = false
                    }
                } else {
                    campo.append(c)
                }
            } else {
                when (c) {
                    '"' -> entreAspas = true
                    separador -> { linha.add(campo.toString()); campo.clear() }
                    '\r' -> Unit
                    '\n' -> {
                        linha.add(campo.toString()); campo.clear()
                        linhas.add(linha); linha = mutableListOf()
                    }
                    else -> campo.append(c)
                }
            }
            i++
        }
        if (campo.isNotEmpty() || linha.isNotEmpty()) {
            linha.add(campo.toString())
            linhas.add(linha)
        }
        return linhas
    }

    fun escrever(linhas: List<List<String>>, formato: FormatoCsv, saida: OutputStream) {
        val texto = buildString {
            linhas.forEach { campos ->
                append(campos.joinToString(formato.separador.toString()) { escapar(it, formato.separador) })
                append(formato.quebraLinha)
            }
        }
        if (formato.comBom) saida.write(BOM_UTF8)
        saida.write(texto.toByteArray(formato.charset))
    }

    private fun escapar(campo: String, separador: Char): String {
        val precisa = campo.any { it == separador || it == '"' || it == '\n' || it == '\r' }
        return if (precisa) "\"" + campo.replace("\"", "\"\"") + "\"" else campo
    }
}
