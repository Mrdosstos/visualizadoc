package br.com.mrdosstos.visualizadoc.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** Documento selecionado pelo usuário, identificado pela sua Uri (SAF). */
data class DocumentoAberto(
    val uri: Uri,
    val nome: String,
    val tipo: TipoDocumento,
    val tamanhoBytes: Long?,
)

object ArquivoUtils {

    /** Lê nome, tamanho e tipo do documento a partir da Uri. */
    fun lerInfo(context: Context, uri: Uri): DocumentoAberto {
        var nome: String? = null
        var tamanho: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idxNome = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val idxTamanho = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idxNome >= 0 && !cursor.isNull(idxNome)) nome = cursor.getString(idxNome)
                if (idxTamanho >= 0 && !cursor.isNull(idxTamanho)) tamanho = cursor.getLong(idxTamanho)
            }
        }
        val nomeFinal = nome ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento"
        val mime = context.contentResolver.getType(uri)
        return DocumentoAberto(uri, nomeFinal, TipoDocumento.detectar(mime, nomeFinal), tamanho)
    }

    /**
     * Mantém a permissão de acesso à Uri depois que o app é fechado,
     * para que a lista de recentes continue funcionando.
     * Tenta leitura + escrita (necessária na edição) e cai para só leitura.
     */
    fun manterPermissao(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Provedor não oferece permissão persistente; o arquivo abre só nesta sessão.
            }
        }
    }
}
