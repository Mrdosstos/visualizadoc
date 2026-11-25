package br.com.mrdosstos.visualizadoc.core

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object Compartilhar {
    /** Abre o "Compartilhar" do Android (WhatsApp, e-mail, Drive...) com o arquivo. */
    fun enviar(context: Context, uri: Uri, mime: String) {
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        abrirEscolha(context, envio)
    }

    /** Vários arquivos de uma vez (seleção múltipla). */
    fun enviarVarios(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            enviar(context, uris[0], "*/*")
            return
        }
        val envio = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newRawUri("", uris[0]).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        abrirEscolha(context, envio)
    }

    /** Indica o app para alguém, com o link das versões no GitHub. */
    fun indicarApp(context: Context) {
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "VisualizaDoc: leitor e editor de PDF, Word e planilhas. Grátis e sem anúncios.\n" +
                    "https://github.com/Mrdosstos/visualizadoc/releases/latest",
            )
        }
        abrirEscolha(context, envio)
    }

    private fun abrirEscolha(context: Context, envio: Intent) {
        try {
            context.startActivity(Intent.createChooser(envio, "Compartilhar"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app disponível para compartilhar.", Toast.LENGTH_SHORT).show()
        }
    }
}
