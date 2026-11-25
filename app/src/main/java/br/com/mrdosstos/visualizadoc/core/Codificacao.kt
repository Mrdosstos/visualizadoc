package br.com.mrdosstos.visualizadoc.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Texto lido de um arquivo, com o que é preciso para gravá-lo de volta igual. */
data class TextoDecodificado(val texto: String, val charset: Charset, val comBom: Boolean)

object Codificacao {
    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** UTF-8 se for válido; senão, Windows-1252 (padrão de arquivos antigos do Windows). */
    fun decodificar(bytes: ByteArray): TextoDecodificado {
        val comBom = bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(BOM_UTF8)
        val conteudo = if (comBom) bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            val texto = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(conteudo))
                .toString()
            TextoDecodificado(texto, Charsets.UTF_8, comBom)
        } catch (_: CharacterCodingException) {
            val windows = Charset.forName("windows-1252")
            TextoDecodificado(String(conteudo, windows), windows, false)
        }
    }

    fun codificar(texto: String, charset: Charset, comBom: Boolean): ByteArray {
        val corpo = texto.toByteArray(charset)
        return if (comBom) BOM_UTF8 + corpo else corpo
    }
}
