package br.com.mrdosstos.visualizadoc.core

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import java.io.OutputStream

sealed interface ResultadoSalvar {
    data object Ok : ResultadoSalvar
    /** Sem permissão de escrita no arquivo original: oferecer "Salvar como". */
    data object PrecisaSalvarComo : ResultadoSalvar
    data class Erro(val mensagem: String) : ResultadoSalvar
}

object Gravacao {
    /** Abre o destino substituindo o conteúdo ("wt"); alguns provedores só aceitam "w". */
    fun abrirSaida(context: Context, uri: Uri): OutputStream {
        val resolver = context.contentResolver
        return try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } ?: throw FileNotFoundException("Destino indisponível")
    }
}
