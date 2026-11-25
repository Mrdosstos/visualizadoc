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
        try {
            context.startActivity(Intent.createChooser(envio, "Compartilhar"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app disponível para compartilhar.", Toast.LENGTH_SHORT).show()
        }
    }
}
